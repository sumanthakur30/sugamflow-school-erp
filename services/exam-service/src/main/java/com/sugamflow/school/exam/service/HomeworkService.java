package com.sugamflow.school.exam.service;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.integration.AcademicClient;
import com.sugamflow.school.exam.integration.ConfigEngineClient;
import com.sugamflow.school.exam.integration.StudentAccessClient;
import com.sugamflow.school.exam.integration.StudentDirectoryClient;
import com.sugamflow.school.exam.persistence.entity.HomeworkEntity;
import com.sugamflow.school.exam.persistence.entity.HomeworkSubmissionEntity;
import com.sugamflow.school.exam.persistence.repo.HomeworkRepository;
import com.sugamflow.school.exam.persistence.repo.HomeworkSubmissionRepository;
import com.sugamflow.school.exam.web.ExamException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Native LMS homework — staff/teacher manage; parent/student see linked children via /mine. */
@Service
public class HomeworkService {

  public static final String FEATURE_LMS = "FEATURE_LMS";

  private final HomeworkRepository homework;
  private final HomeworkSubmissionRepository submissions;
  private final ConfigEngineClient engines;
  private final StudentAccessClient studentAccess;
  private final AcademicClient academic;
  private final StudentDirectoryClient directory;

  public HomeworkService(
      HomeworkRepository homework,
      HomeworkSubmissionRepository submissions,
      ConfigEngineClient engines,
      StudentAccessClient studentAccess,
      AcademicClient academic,
      StudentDirectoryClient directory) {
    this.homework = homework;
    this.submissions = submissions;
    this.engines = engines;
    this.studentAccess = studentAccess;
    this.academic = academic;
    this.directory = directory;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String status, String sectionId) {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    denyPortalReadOnlyList(scope);
    UUID sectionFilter = parseUuid(sectionId);
    if (sectionFilter != null) {
      requireSectionAccess(scope, sectionFilter);
    }
    List<HomeworkEntity> rows =
        status == null || status.isBlank()
            ? homework.findByOrganizationIdOrderByDueAtAscCreatedAtDesc(scope.organizationId())
            : homework.findByOrganizationIdAndStatusOrderByDueAtAscCreatedAtDesc(
                scope.organizationId(), status.trim().toUpperCase(Locale.ROOT));
    List<Map<String, Object>> out = new ArrayList<>();
    for (HomeworkEntity row : rows) {
      if (sectionFilter != null
          && (row.getSectionId() == null || !sectionFilter.equals(row.getSectionId()))) {
        continue;
      }
      if (!canViewAssignment(scope, row)) {
        continue;
      }
      out.add(hwDto(row));
    }
    return out;
  }

  /** Parent/student: published homework for linked children, with per-child submission summary. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listMine() {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    AccessScope access = studentAccess.resolve(scope);
    List<Map<String, Object>> children = linkedChildren(scope, access);
    if (children.isEmpty()) {
      return List.of();
    }
    List<HomeworkEntity> rows =
        homework.findByOrganizationIdAndStatusOrderByDueAtAscCreatedAtDesc(
            scope.organizationId(), HomeworkEntity.STATUS_PUBLISHED);
    List<Map<String, Object>> out = new ArrayList<>();
    for (HomeworkEntity hw : rows) {
      for (Map<String, Object> child : children) {
        if (!homeworkMatchesChild(hw, child)) {
          continue;
        }
        Map<String, Object> row = hwDto(hw);
        row.put("studentId", child.get("id"));
        row.put("admissionNo", child.get("admissionNo"));
        row.put(
            "studentName",
            firstNonBlank(str(child.get("fullName")), str(child.get("studentName")), str(child.get("admissionNo"))));
        row.put("classSection", firstNonBlank(str(child.get("classSection")), hw.getClassSection()));
        HomeworkSubmissionEntity sub = findSubmission(hw.getId(), child);
        row.put("submission", sub == null ? null : subDto(sub));
        row.put("submissionStatus", sub == null ? "PENDING" : sub.getStatus());
        out.add(row);
      }
    }
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    if (PersonaRoles.isPortalReadOnly(scope.roleCode())) {
      throw new SecurityException("Use /api/exam/homework/mine for portal homework detail");
    }
    HomeworkEntity row = requireHomework(scope, id);
    requireCanView(scope, row);
    Map<String, Object> out = hwDto(row);
    out.put(
        "submissions",
        submissions
            .findByOrganizationIdAndHomeworkIdOrderBySubmittedAtDesc(scope.organizationId(), id)
            .stream()
            .map(this::subDto)
            .toList());
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listSubmissions(UUID id) {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    denyPortalReadOnlyList(scope);
    HomeworkEntity row = requireHomework(scope, id);
    requireCanView(scope, row);
    return submissions
        .findByOrganizationIdAndHomeworkIdOrderBySubmittedAtDesc(scope.organizationId(), id)
        .stream()
        .map(this::subDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    requireHomeworkEnabled(scope);
    PersonaRoles.requireStaffWrite(scope);
    String title = str(body.get("title"));
    if (title == null) {
      throw new ExamException("VALIDATION", "title is required");
    }
    UUID sectionId = parseUuid(body.get("sectionId"));
    String classSection = str(body.get("classSection"));
    if (sectionId != null) {
      requireSectionAccess(scope, sectionId);
      if (classSection == null) {
        Map<String, Object> section = academic.getSection(scope, sectionId.toString());
        classSection =
            firstNonBlank(str(section.get("studentLabel")), str(section.get("name")));
      }
    } else if (PersonaRoles.isTeacher(scope.roleCode())) {
      throw new ExamException("VALIDATION", "sectionId is required for teachers");
    }
    HomeworkEntity row = new HomeworkEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(scope.organizationId());
    row.setBranchId(scope.branchId());
    row.setAcademicSessionId(scope.academicSessionId());
    row.setTitle(title);
    row.setDescription(str(body.get("description")));
    row.setSubjectKey(strOr(body.get("subjectKey"), "general"));
    row.setClassSection(classSection);
    row.setSectionId(sectionId);
    row.setDueAt(parseInstant(body.get("dueAt"), body.get("dueDate")));
    String status = strOr(body.get("status"), HomeworkEntity.STATUS_PUBLISHED).toUpperCase(Locale.ROOT);
    row.setStatus(status);
    row.setCreatedBy(scope.userId());
    row.setCreatedAt(Instant.now());
    row.setUpdatedAt(Instant.now());
    return hwDto(homework.save(row));
  }

  @Transactional
  public Map<String, Object> updateStatus(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    PersonaRoles.requireStaffWrite(scope);
    HomeworkEntity row = requireHomework(scope, id);
    requireCanView(scope, row);
    String status = str(body.get("status"));
    if (status == null) {
      throw new ExamException("VALIDATION", "status is required");
    }
    row.setStatus(status.toUpperCase(Locale.ROOT));
    row.setUpdatedAt(Instant.now());
    return hwDto(homework.save(row));
  }

  @Transactional
  public Map<String, Object> submit(UUID homeworkId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    requireHomeworkEnabled(scope);
    if (PersonaRoles.isPortalReadOnly(scope.roleCode())) {
      return submitMine(homeworkId, body);
    }
    PersonaRoles.requireStaffWrite(scope);
    HomeworkEntity hw = requireHomework(scope, homeworkId);
    requireCanView(scope, hw);
    return upsertSubmission(hw, body, false);
  }

  /** Parent/student submission for a linked child. */
  @Transactional
  public Map<String, Object> submitMine(UUID homeworkId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    requireHomeworkEnabled(scope);
    if (PersonaRoles.isParent(scope.roleCode()) && !parentSubmissionEnabled(scope)) {
      throw new SecurityException("Parent homework submission is disabled");
    }
    if (!PersonaRoles.isPortalReadOnly(scope.roleCode())
        && !PersonaRoles.isStudent(scope.roleCode())
        && !PersonaRoles.isParent(scope.roleCode())) {
      // elevated staff can still use this path for smoke/testing
    }
    HomeworkEntity hw = requireHomework(scope, homeworkId);
    if (!HomeworkEntity.STATUS_PUBLISHED.equalsIgnoreCase(hw.getStatus())) {
      throw new ExamException("CONFLICT", "Homework is not open for submission");
    }
    AccessScope access = studentAccess.resolve(scope);
    Map<String, Object> child = resolveLinkedChild(scope, access, body);
    if (!homeworkMatchesChild(hw, child)) {
      throw new ExamException("NOT_FOUND", "Homework not available for this student");
    }
    Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
    payload.put("studentId", child.get("id"));
    payload.put("admissionNo", child.get("admissionNo"));
    payload.put(
        "studentName",
        firstNonBlank(str(child.get("fullName")), str(child.get("studentName")), str(body.get("studentName"))));
    return upsertSubmission(hw, payload, true);
  }

  @Transactional
  public Map<String, Object> grade(UUID submissionId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLms(scope);
    PersonaRoles.requireStaffWrite(scope);
    HomeworkSubmissionEntity row =
        submissions
            .findByIdAndOrganizationId(submissionId, scope.organizationId())
            .orElseThrow(() -> new ExamException("NOT_FOUND", "Submission not found"));
    HomeworkEntity hw = requireHomework(scope, row.getHomeworkId());
    requireCanView(scope, hw);
    if (body.get("marks") != null) {
      row.setGradedMarks(toDecimal(body.get("marks")));
    }
    if (body.containsKey("feedback")) {
      row.setFeedback(str(body.get("feedback")));
    }
    row.setStatus(HomeworkSubmissionEntity.STATUS_GRADED);
    row.setUpdatedAt(Instant.now());
    return subDto(submissions.save(row));
  }

  private Map<String, Object> upsertSubmission(
      HomeworkEntity hw, Map<String, Object> body, boolean portal) {
    if (HomeworkEntity.STATUS_CLOSED.equalsIgnoreCase(hw.getStatus())) {
      throw new ExamException("CONFLICT", "Homework is closed");
    }
    String admission = str(body.get("admissionNo"));
    UUID studentId = parseUuid(body.get("studentId"));
    if (admission == null && studentId == null) {
      throw new ExamException("VALIDATION", "admissionNo or studentId is required");
    }
    HomeworkSubmissionEntity existing = null;
    if (studentId != null) {
      existing = submissions.findByHomeworkIdAndStudentId(hw.getId(), studentId).orElse(null);
    }
    if (existing == null && admission != null) {
      existing =
          submissions
              .findByHomeworkIdAndAdmissionNoIgnoreCase(hw.getId(), admission)
              .orElse(null);
    }
    HomeworkSubmissionEntity row = existing != null ? existing : new HomeworkSubmissionEntity();
    if (existing == null) {
      row.setId(UUID.randomUUID());
      row.setOrganizationId(hw.getOrganizationId());
      row.setHomeworkId(hw.getId());
      row.setCreatedAt(Instant.now());
    }
    row.setStudentId(studentId != null ? studentId : row.getStudentId());
    row.setAdmissionNo(admission != null ? admission : row.getAdmissionNo());
    row.setStudentName(str(body.get("studentName")));
    row.setBody(str(body.get("body")));
    row.setStatus(HomeworkSubmissionEntity.STATUS_SUBMITTED);
    row.setSubmittedAt(Instant.now());
    row.setUpdatedAt(Instant.now());
    if (portal) {
      row.setGradedMarks(null);
      row.setFeedback(null);
    }
    return subDto(submissions.save(row));
  }

  private HomeworkEntity requireHomework(TenantScope scope, UUID id) {
    return homework
        .findByIdAndOrganizationId(id, scope.organizationId())
        .orElseThrow(() -> new ExamException("NOT_FOUND", "Homework not found"));
  }

  private void requireCanView(TenantScope scope, HomeworkEntity row) {
    if (!canViewAssignment(scope, row)) {
      throw new SecurityException("Not allowed to access this homework");
    }
  }

  private boolean canViewAssignment(TenantScope scope, HomeworkEntity row) {
    if (!PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      return true;
    }
    if (PersonaRoles.isTeacher(scope.roleCode())) {
      if (row.getSectionId() == null) {
        return true;
      }
      return teacherOwnsSection(academic.teacherScope(scope), row.getSectionId());
    }
    return false;
  }

  private void requireSectionAccess(TenantScope scope, UUID sectionId) {
    if (!PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      return;
    }
    if (!PersonaRoles.isTeacher(scope.roleCode())) {
      throw new SecurityException("Only staff/teachers can manage homework for a section");
    }
    if (!teacherOwnsSection(academic.teacherScope(scope), sectionId)) {
      throw new SecurityException("Teacher is not assigned to section " + sectionId);
    }
  }

  private static boolean teacherOwnsSection(Map<String, Object> teacherScope, UUID sectionId) {
    Object ids = teacherScope.get("sectionIds");
    if (ids instanceof List<?> list) {
      for (Object id : list) {
        if (sectionId.toString().equalsIgnoreCase(String.valueOf(id))) {
          return true;
        }
      }
    }
    return false;
  }

  private static void denyPortalReadOnlyList(TenantScope scope) {
    if (PersonaRoles.isPortalReadOnly(scope.roleCode())) {
      throw new SecurityException("Use /api/exam/homework/mine for portal homework lists");
    }
  }

  private List<Map<String, Object>> linkedChildren(TenantScope scope, AccessScope access) {
    List<Map<String, Object>> students = directory.listAccessible(scope, 100);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> student : students) {
      if (access.restricted() && !access.allowsStudentDto(student)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", student.get("id"));
      row.put("admissionNo", student.get("admissionNo"));
      Object answers = student.get("answers");
      if (answers instanceof Map<?, ?> a) {
        row.put("fullName", a.get("fullName") != null ? a.get("fullName") : a.get("studentName"));
        row.put(
            "classSection",
            firstNonBlank(str(a.get("classSection")), str(a.get("classApplied"))));
        row.put("studentName", a.get("studentName"));
      } else {
        row.put("fullName", student.get("fullName"));
        row.put("classSection", student.get("classSection"));
        row.put("studentName", student.get("studentName"));
      }
      if (row.get("classSection") == null) {
        row.put("classSection", student.get("classSection"));
      }
      out.add(row);
    }
    return out;
  }

  private Map<String, Object> resolveLinkedChild(
      TenantScope scope, AccessScope access, Map<String, Object> body) {
    List<Map<String, Object>> children = linkedChildren(scope, access);
    if (children.isEmpty()) {
      throw new ExamException("NOT_FOUND", "No linked students for homework submission");
    }
    UUID studentId = parseUuid(body == null ? null : body.get("studentId"));
    String admission = str(body == null ? null : body.get("admissionNo"));
    for (Map<String, Object> child : children) {
      if (studentId != null && studentId.toString().equalsIgnoreCase(str(child.get("id")))) {
        return child;
      }
      if (admission != null && admission.equalsIgnoreCase(str(child.get("admissionNo")))) {
        return child;
      }
    }
    if (children.size() == 1 && studentId == null && admission == null) {
      return children.get(0);
    }
    throw new ExamException("VALIDATION", "studentId or admissionNo must match a linked child");
  }

  private static boolean homeworkMatchesChild(HomeworkEntity hw, Map<String, Object> child) {
    String hwClass = str(hw.getClassSection());
    String childClass = str(child.get("classSection"));
    if (hwClass == null || hwClass.isBlank()) {
      return true;
    }
    return childClass != null && hwClass.equalsIgnoreCase(childClass);
  }

  private HomeworkSubmissionEntity findSubmission(UUID homeworkId, Map<String, Object> child) {
    UUID studentId = parseUuid(child.get("id"));
    if (studentId != null) {
      var byId = submissions.findByHomeworkIdAndStudentId(homeworkId, studentId);
      if (byId.isPresent()) {
        return byId.get();
      }
    }
    String admission = str(child.get("admissionNo"));
    if (admission != null) {
      return submissions.findByHomeworkIdAndAdmissionNoIgnoreCase(homeworkId, admission).orElse(null);
    }
    return null;
  }

  private void requireLms(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FEATURE_LMS)) {
      throw new ExamException("FEATURE_DISABLED", "FEATURE_LMS is off for this subscription plan.");
    }
  }

  private void requireHomeworkEnabled(TenantScope scope) {
    Map<String, Object> settings = lmsSettings(scope);
    if (Boolean.FALSE.equals(settings.get("homeworkEnabled"))) {
      throw new ExamException("FEATURE_DISABLED", "Native homework is disabled in LMS settings.");
    }
  }

  private boolean parentSubmissionEnabled(TenantScope scope) {
    Map<String, Object> settings = lmsSettings(scope);
    return !Boolean.FALSE.equals(settings.get("parentSubmissionEnabled"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> lmsSettings(TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, "lms");
    Object settings = module.get("settings");
    if (settings instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    return module;
  }

  private Map<String, Object> hwDto(HomeworkEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId().toString());
    m.put("title", e.getTitle());
    m.put("description", e.getDescription());
    m.put("subjectKey", e.getSubjectKey());
    m.put("classSection", e.getClassSection());
    m.put("sectionId", e.getSectionId() == null ? null : e.getSectionId().toString());
    m.put("dueAt", e.getDueAt() == null ? null : e.getDueAt().toString());
    m.put("status", e.getStatus());
    m.put("createdBy", e.getCreatedBy());
    m.put("createdAt", e.getCreatedAt().toString());
    m.put("updatedAt", e.getUpdatedAt().toString());
    return m;
  }

  private Map<String, Object> subDto(HomeworkSubmissionEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId().toString());
    m.put("homeworkId", e.getHomeworkId().toString());
    m.put("studentId", e.getStudentId() == null ? null : e.getStudentId().toString());
    m.put("admissionNo", e.getAdmissionNo());
    m.put("studentName", e.getStudentName());
    m.put("body", e.getBody());
    m.put("status", e.getStatus());
    m.put("submittedAt", e.getSubmittedAt().toString());
    m.put("gradedMarks", e.getGradedMarks());
    m.put("feedback", e.getFeedback());
    return m;
  }

  private static Instant parseInstant(Object dueAt, Object dueDate) {
    String at = str(dueAt);
    if (at != null) {
      try {
        return Instant.parse(at);
      } catch (Exception ignored) {
        // fall through
      }
    }
    String date = str(dueDate);
    if (date != null) {
      try {
        return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  private static BigDecimal toDecimal(Object raw) {
    if (raw instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    String s = str(raw);
    if (s == null) {
      return null;
    }
    try {
      return new BigDecimal(s);
    } catch (Exception ex) {
      return null;
    }
  }

  private static UUID parseUuid(Object v) {
    if (v == null) {
      return null;
    }
    try {
      return UUID.fromString(String.valueOf(v));
    } catch (Exception ex) {
      return null;
    }
  }

  private static String str(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String strOr(Object v, String fallback) {
    String s = str(v);
    return s == null ? fallback : s;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }
}
