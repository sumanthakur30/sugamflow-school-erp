package com.sugamflow.school.exam.service;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.integration.AcademicClient;
import com.sugamflow.school.exam.integration.StudentAccessClient;
import com.sugamflow.school.exam.integration.StudentDirectoryClient;
import com.sugamflow.school.exam.persistence.entity.ExamDefinitionEntity;
import com.sugamflow.school.exam.persistence.entity.ExamEntryPolicyEntity;
import com.sugamflow.school.exam.persistence.entity.ExamMarkAuditEntity;
import com.sugamflow.school.exam.persistence.entity.ExamMarkEntity;
import com.sugamflow.school.exam.persistence.repo.ExamDefinitionRepository;
import com.sugamflow.school.exam.persistence.repo.ExamEntryPolicyRepository;
import com.sugamflow.school.exam.persistence.repo.ExamMarkAuditRepository;
import com.sugamflow.school.exam.persistence.repo.ExamMarkRepository;
import com.sugamflow.school.exam.web.ExamException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Teacher gradebook: exam definitions + section/subject marks grid with draft, submit and lock. */
@Service
public class GradebookService {

  static final Set<String> EXCUSED =
      Set.of("ABSENT", "NOT_APPEARED", "EXEMPTED", "MEDICAL_LEAVE", "WITHHELD");
  private static final Set<String> READ_ONLY =
      Set.of("SUBMITTED", "PUBLISHED", "LOCKED", "CORRECTION_REQUESTED");

  private final ExamDefinitionRepository definitions;
  private final ExamMarkRepository marks;
  private final ExamMarkAuditRepository audits;
  private final ExamEntryPolicyRepository policies;
  private final AcademicClient academic;
  private final StudentDirectoryClient directory;
  private final StudentAccessClient studentAccess;
  private final GradingSupport grading;

  public GradebookService(
      ExamDefinitionRepository definitions,
      ExamMarkRepository marks,
      ExamMarkAuditRepository audits,
      ExamEntryPolicyRepository policies,
      AcademicClient academic,
      StudentDirectoryClient directory,
      StudentAccessClient studentAccess,
      GradingSupport grading) {
    this.definitions = definitions;
    this.marks = marks;
    this.audits = audits;
    this.policies = policies;
    this.academic = academic;
    this.directory = directory;
    this.studentAccess = studentAccess;
    this.grading = grading;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listDefinitions(UUID sectionId, UUID subjectId) {
    TenantScope scope = TenantContext.require();
    List<ExamDefinitionEntity> found;
    if (sectionId != null && subjectId != null) {
      found =
          definitions.findByOrganizationIdAndSectionIdAndSubjectIdOrderByUpdatedAtDesc(
              scope.organizationId(), sectionId, subjectId);
    } else if (sectionId != null) {
      found =
          definitions.findByOrganizationIdAndSectionIdOrderByUpdatedAtDesc(
              scope.organizationId(), sectionId);
    } else {
      found = definitions.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId());
    }
    found = filterSession(found, scope);
    if (PersonaRoles.isTeacher(scope.roleCode())) {
      Map<String, Object> teacherScope = academic.teacherScope(scope);
      found =
          found.stream()
              .filter(d -> teacherOwnsSubject(teacherScope, d.getSectionId(), d.getSubjectId()))
              .toList();
    }
    return found.stream().map(d -> definitionToMap(d, scope)).toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> entryBoard() {
    TenantScope scope = TenantContext.require();
    List<ExamDefinitionEntity> found =
        filterSession(definitions.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId()), scope);
    Map<String, Object> teacherScope = null;
    if (PersonaRoles.isTeacher(scope.roleCode())) {
      teacherScope = academic.teacherScope(scope);
      Map<String, Object> scopeView = teacherScope;
      found =
          found.stream()
              .filter(d -> teacherOwnsSubject(scopeView, d.getSectionId(), d.getSubjectId()))
              .toList();
    }
    Map<String, String> subjectNames = subjectNames(scope);
    Map<String, String> classNames = classNames(scope);
    Map<String, Map<String, Object>> sections = sectionIndex(scope);
    Map<String, String> teachers = assignmentTeachers(scope);
    Map<String, List<Map<String, Object>>> rosterCache = new LinkedHashMap<>();
    ExamEntryPolicyEntity policy = policy(scope);
    List<Map<String, Object>> out = new ArrayList<>();
    for (ExamDefinitionEntity exam : found) {
      Map<String, Object> section = sections.getOrDefault(exam.getSectionId().toString(), Map.of());
      String label =
          firstNonBlank(
              asString(section.get("studentLabel")),
              asString(section.get("name")),
              exam.getSectionId().toString());
      List<Map<String, Object>> roster =
          rosterCache.computeIfAbsent(label, key -> directory.listByClassSection(scope, key, 500));
      List<ExamMarkEntity> saved = marks.findByExamDefinitionIdOrderByStudentNameAsc(exam.getId());
      int[] counts = countProgress(roster, saved, policy);
      Map<String, Object> row = definitionToMap(exam, scope);
      row.put("sectionLabel", label);
      row.put("className", classNames.get(asString(section.get("classId"))));
      row.put("subjectName", subjectNames.getOrDefault(exam.getSubjectId().toString(), "Subject"));
      String teacher =
          teachers.get(exam.getSectionId() + "|" + exam.getSubjectId());
      row.put("teacherName", firstNonBlank(teacher, exam.getCreatedBy()));
      row.put("total", counts[0]);
      row.put("completed", counts[1]);
      row.put("pending", counts[2]);
      row.put("progressPercent", counts[0] == 0 ? 0 : (counts[1] * 100) / counts[0]);
      out.add(row);
    }
    return out;
  }

  @Transactional
  public Map<String, Object> createDefinition(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    UUID sectionId = parseUuid(body.get("sectionId"));
    UUID subjectId = parseUuid(body.get("subjectId"));
    String name = asString(body.get("name"));
    String termKey = asString(body.get("termKey"));
    BigDecimal maxMarks = toDecimal(body.get("maxMarks"));
    if (sectionId == null || subjectId == null || name == null || termKey == null) {
      throw new ExamException("VALIDATION", "sectionId, subjectId, name and termKey are required");
    }
    if (maxMarks.compareTo(BigDecimal.ZERO) <= 0) {
      throw new ExamException("VALIDATION", "maxMarks must be > 0");
    }
    requireSubjectAccess(scope, sectionId, subjectId);

    ExamDefinitionEntity e = new ExamDefinitionEntity();
    e.setId(UUID.randomUUID());
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(scope.branchId());
    e.setAcademicSessionId(scope.academicSessionId());
    e.setSectionId(sectionId);
    e.setSubjectId(subjectId);
    e.setName(name);
    e.setTermKey(termKey.toUpperCase(Locale.ROOT));
    e.setMaxMarks(maxMarks);
    BigDecimal passing = toDecimalNullable(body.get("passingMarks"));
    e.setPassingMarks(passing);
    e.setExamDate(parseDate(body.get("examDate")));
    e.setComponents(componentsFrom(body.get("components")));
    e.setStatus("OPEN");
    e.setCreatedBy(scope.userId());
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    return definitionToMap(definitions.save(e), scope);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> gradebook(UUID examDefinitionId) {
    TenantScope scope = TenantContext.require();
    ExamDefinitionEntity exam = requireDefinition(examDefinitionId, scope.organizationId());
    if (PersonaRoles.isTeacher(scope.roleCode())) {
      requireSubjectAccess(scope, exam.getSectionId(), exam.getSubjectId());
    }

    Map<String, Object> section = academic.getSection(scope, exam.getSectionId().toString());
    String label =
        firstNonBlank(
            asString(section.get("studentLabel")),
            asString(section.get("name")),
            exam.getSectionId().toString());

    Map<UUID, ExamMarkEntity> byStudent = new LinkedHashMap<>();
    Map<String, ExamMarkEntity> byAdmission = new LinkedHashMap<>();
    List<ExamMarkEntity> saved = marks.findByExamDefinitionIdOrderByStudentNameAsc(exam.getId());
    for (ExamMarkEntity m : saved) {
      if (m.getStudentId() != null) {
        byStudent.put(m.getStudentId(), m);
      }
      if (m.getAdmissionNo() != null) {
        byAdmission.put(m.getAdmissionNo().toLowerCase(Locale.ROOT), m);
      }
    }

    ExamEntryPolicyEntity policy = policy(scope);
    List<Map<String, Object>> bands = grading == null ? GradingSupport.defaultBands() : grading.bandsFor(scope);
    if (bands == null || bands.isEmpty()) {
      bands = GradingSupport.defaultBands();
    }
    List<Map<String, Object>> students = new ArrayList<>();
    Set<UUID> seenStudents = new HashSet<>();
    Set<String> seenAdmissions = new HashSet<>();
    for (Map<String, Object> row : directory.listByClassSection(scope, label, 500)) {
      UUID studentId = parseUuid(row.get("id"));
      String admission = asString(row.get("admissionNo"));
      ExamMarkEntity existing = matchMark(byStudent, byAdmission, studentId, admission);
      if (studentId != null) {
        seenStudents.add(studentId);
      }
      if (admission != null) {
        seenAdmissions.add(admission.toLowerCase(Locale.ROOT));
      }
      students.add(studentRow(row, existing, exam, policy, bands, false));
    }
    for (ExamMarkEntity m : saved) {
      boolean seen =
          (m.getStudentId() != null && seenStudents.contains(m.getStudentId()))
              || (m.getAdmissionNo() != null
                  && seenAdmissions.contains(m.getAdmissionNo().toLowerCase(Locale.ROOT)));
      if (seen) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", m.getStudentId() == null ? null : m.getStudentId().toString());
      row.put("admissionNo", m.getAdmissionNo());
      row.put("fullName", m.getStudentName());
      row.put("rollNo", m.getRollNo());
      students.add(studentRow(row, m, exam, policy, bands, true));
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("exam", definitionToMap(exam, scope));
    result.put("sectionLabel", label);
    result.put("policy", policyMap(policy));
    result.put("readOnly", READ_ONLY.contains(exam.getStatus()));
    result.put("canUnlock", canUnlock(scope, exam.getSectionId()));
    result.put("students", students);
    result.put("progress", progressMap(students));
    result.put("summary", summaryMap(students, exam));
    return result;
  }

  @Transactional
  public Map<String, Object> bulkSave(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    UUID examId = parseUuid(body.get("examDefinitionId"));
    if (examId == null) {
      throw new ExamException("VALIDATION", "examDefinitionId is required");
    }
    ExamDefinitionEntity exam = requireDefinition(examId, scope.organizationId());
    requireSubjectAccess(scope, exam.getSectionId(), exam.getSubjectId());
    assertWritable(exam);
    assertFresh(exam, body.get("expectedUpdatedAt"));

    Object raw = body.get("marks");
    if (!(raw instanceof List<?> list)) {
      throw new ExamException("VALIDATION", "marks array is required");
    }
    Set<String> seen = new HashSet<>();
    String reason = asString(body.get("reason"));
    ExamEntryPolicyEntity policy = policy(scope);
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> m)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> mark = (Map<String, Object>) m;
      String key = studentKey(mark);
      if (key != null && !seen.add(key)) {
        throw new ExamException("VALIDATION", "Duplicate student in marks: " + key);
      }
      upsertMark(exam, scope, mark, policy, reason);
    }
    if ("OPEN".equals(exam.getStatus()) || "DRAFT".equals(exam.getStatus())) {
      exam.setStatus("OPEN");
    }
    exam.setUpdatedAt(Instant.now());
    definitions.save(exam);
    return gradebook(exam.getId());
  }

  @Transactional
  public Map<String, Object> submit(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    ExamDefinitionEntity exam = requireDefinition(id, scope.organizationId());
    requireSubjectAccess(scope, exam.getSectionId(), exam.getSubjectId());
    if (!"OPEN".equals(exam.getStatus()) && !"DRAFT".equals(exam.getStatus())) {
      throw new ExamException("VALIDATION", "Only a draft can be submitted");
    }
    Map<String, Object> book = gradebook(id);
    @SuppressWarnings("unchecked")
    Map<String, Object> progress = (Map<String, Object>) book.get("progress");
    int pending = progress == null ? 0 : ((Number) progress.getOrDefault("pending", 0)).intValue();
    ExamEntryPolicyEntity policy = policy(scope);
    boolean anyway = Boolean.TRUE.equals(body.get("submitAnyway"));
    if (pending > 0 && !(policy.isAllowIncompleteSubmit() && anyway)) {
      throw new ExamException(
          "INCOMPLETE",
          pending + " students are still pending.");
    }
    exam.setStatus("SUBMITTED");
    exam.setUpdatedAt(Instant.now());
    definitions.save(exam);
    return gradebook(exam.getId());
  }

  @Transactional
  public Map<String, Object> requestCorrection(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    ExamDefinitionEntity exam = requireDefinition(id, scope.organizationId());
    requireSubjectAccess(scope, exam.getSectionId(), exam.getSubjectId());
    if (!"SUBMITTED".equals(exam.getStatus()) && !"PUBLISHED".equals(exam.getStatus())) {
      throw new ExamException("VALIDATION", "Correction can be requested after submit");
    }
    String reason = asString(body.get("reason"));
    if (reason == null) {
      throw new ExamException("VALIDATION", "A reason is required");
    }
    exam.setStatus("CORRECTION_REQUESTED");
    exam.setUpdatedAt(Instant.now());
    definitions.save(exam);
    writeAudit(exam, scope, null, null, null, null, null, null, null, reason);
    return definitionToMap(exam, scope);
  }

  @Transactional
  public Map<String, Object> unlock(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    ExamDefinitionEntity exam = requireDefinition(id, scope.organizationId());
    if (!canUnlock(scope, exam.getSectionId())) {
      throw new SecurityException("Only a principal, admin, or class teacher can unlock marks");
    }
    if ("OPEN".equals(exam.getStatus()) || "DRAFT".equals(exam.getStatus())) {
      return definitionToMap(exam, scope);
    }
    String reason = asString(body == null ? null : body.get("reason"));
    exam.setStatus("OPEN");
    exam.setLockedAt(null);
    exam.setUpdatedAt(Instant.now());
    definitions.save(exam);
    writeAudit(exam, scope, null, null, null, null, null, null, null, firstNonBlank(reason, "Unlocked for correction"));
    return definitionToMap(exam, scope);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> history(UUID examDefinitionId, UUID studentId, String admissionNo) {
    TenantScope scope = TenantContext.require();
    ExamDefinitionEntity exam = requireDefinition(examDefinitionId, scope.organizationId());
    if (PersonaRoles.isTeacher(scope.roleCode())) {
      requireSubjectAccess(scope, exam.getSectionId(), exam.getSubjectId());
    }
    List<ExamMarkAuditEntity> rows;
    if (audits == null) {
      rows = List.of();
    } else if (studentId != null) {
      rows = audits.findByExamDefinitionIdAndStudentIdOrderByChangedAtDesc(examDefinitionId, studentId);
    } else if (admissionNo != null && !admissionNo.isBlank()) {
      rows = audits.findByExamDefinitionIdAndAdmissionNoOrderByChangedAtDesc(examDefinitionId, admissionNo);
    } else {
      throw new ExamException("VALIDATION", "studentId or admissionNo is required");
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (ExamMarkAuditEntity row : rows) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("studentName", row.getStudentName());
      m.put("previousMarks", row.getPreviousMarks());
      m.put("newMarks", row.getNewMarks());
      m.put("previousStatus", row.getPreviousStatus());
      m.put("newStatus", row.getNewStatus());
      m.put("reason", row.getReason());
      m.put("changedBy", row.getChangedBy());
      m.put("changedAt", row.getChangedAt() == null ? null : row.getChangedAt().toString());
      m.put("examName", exam.getName());
      out.add(m);
    }
    return out;
  }

  /** Copies one workflow marks form into the gradebook when a matching definition exists. */
  @Transactional
  public void syncIndividual(Map<String, Object> answers) {
    if (answers == null || answers.isEmpty()) {
      return;
    }
    TenantScope scope = TenantContext.require();
    String examName = asString(answers.get("examName"));
    String subjectName = asString(answers.get("subject"));
    String classSection = asString(answers.get("classSection"));
    if (examName == null || subjectName == null) {
      return;
    }
    UUID subjectId = subjectIdByName(scope, subjectName);
    UUID sectionId = sectionIdByLabel(scope, classSection);
    if (subjectId == null) {
      return;
    }
    List<ExamDefinitionEntity> found =
        filterSession(definitions.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId()), scope);
    ExamDefinitionEntity match = null;
    for (ExamDefinitionEntity d : found) {
      if (!examName.equalsIgnoreCase(d.getName())) {
        continue;
      }
      if (!subjectId.equals(d.getSubjectId())) {
        continue;
      }
      if (sectionId != null && !sectionId.equals(d.getSectionId())) {
        continue;
      }
      match = d;
      break;
    }
    if (match == null || READ_ONLY.contains(match.getStatus())) {
      return;
    }
    Map<String, Object> mark = new LinkedHashMap<>();
    mark.put("studentId", answers.get("studentId"));
    mark.put("admissionNo", firstNonBlank(asString(answers.get("admissionNo")), asString(answers.get("admissionNumber"))));
    mark.put("studentName", firstNonBlank(asString(answers.get("studentName")), asString(answers.get("fullName"))));
    mark.put("rollNo", firstNonBlank(asString(answers.get("rollNo")), asString(answers.get("rollNumber"))));
    Object obtained = answers.get("marks");
    if (obtained == null) {
      obtained = answers.get("marksObtained");
    }
    if (obtained == null) {
      obtained = answers.get("score");
    }
    mark.put("marksObtained", obtained);
    mark.put("entryStatus", obtained == null ? null : "PRESENT");
    upsertMark(match, scope, mark, policy(scope), "Individual marks entry");
    match.setUpdatedAt(Instant.now());
    if ("DRAFT".equals(match.getStatus())) {
      match.setStatus("OPEN");
    }
    definitions.save(match);
  }

  @Transactional
  public Map<String, Object> publish(UUID id) {
    return transition(id, "PUBLISHED");
  }

  @Transactional
  public Map<String, Object> lock(UUID id) {
    return transition(id, "LOCKED");
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> publishedMarks() {
    TenantScope scope = TenantContext.require();
    AccessScope access = studentAccess.resolve(scope);
    List<ExamDefinitionEntity> published =
        definitions.findByOrganizationIdAndStatusOrderByUpdatedAtDesc(
            scope.organizationId(), "PUBLISHED");
    List<Map<String, Object>> out = new ArrayList<>();
    for (ExamDefinitionEntity exam : published) {
      for (ExamMarkEntity m : marks.findByExamDefinitionIdOrderByStudentNameAsc(exam.getId())) {
        Map<String, Object> dto = markToMap(m, exam);
        if (access.restricted() && !access.allowsStudentDto(dto)) {
          continue;
        }
        if (!access.restricted() && PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
          continue;
        }
        out.add(dto);
      }
    }
    return out;
  }

  private Map<String, Object> transition(UUID id, String status) {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    ExamDefinitionEntity exam = requireDefinition(id, scope.organizationId());
    requireSubjectAccess(scope, exam.getSectionId(), exam.getSubjectId());
    if ("LOCKED".equals(exam.getStatus()) && !"LOCKED".equals(status)) {
      throw new ExamException("LOCKED", "Exam is locked");
    }
    exam.setStatus(status);
    exam.setUpdatedAt(Instant.now());
    if ("PUBLISHED".equals(status)) {
      exam.setPublishedAt(Instant.now());
    }
    if ("LOCKED".equals(status)) {
      exam.setLockedAt(Instant.now());
    }
    return definitionToMap(definitions.save(exam), scope);
  }

  private void upsertMark(
      ExamDefinitionEntity exam,
      TenantScope scope,
      Map<String, Object> mark,
      ExamEntryPolicyEntity policy,
      String reason) {
    UUID studentId = parseUuid(mark.get("studentId"));
    String admission = asString(mark.get("admissionNo"));
    if (mark.get("marksObtained") != null && toDecimalNullable(mark.get("marksObtained")) == null
        && !isBlank(mark.get("marksObtained"))) {
      throw new ExamException("VALIDATION", "Marks must be a number");
    }
    BigDecimal obtained = toDecimalNullable(mark.get("marksObtained"));
    String status = normalizeStatus(asString(mark.get("entryStatus")));
    if (status != null && EXCUSED.contains(status)) {
      if ("ABSENT".equals(status) && policy.isAbsentAsZero()) {
        obtained = BigDecimal.ZERO;
      } else if (!policy.isAllowMarksWhenExcused()) {
        obtained = null;
      }
    }
    if (obtained != null && obtained.compareTo(exam.getMaxMarks()) > 0) {
      throw new ExamException(
          "VALIDATION",
          "Marks cannot exceed Maximum Marks (" + exam.getMaxMarks().stripTrailingZeros().toPlainString() + ").");
    }
    if (obtained != null && obtained.compareTo(BigDecimal.ZERO) < 0) {
      throw new ExamException("VALIDATION", "Marks cannot be negative");
    }

    ExamMarkEntity entity = null;
    if (studentId != null) {
      entity = marks.findByExamDefinitionIdAndStudentId(exam.getId(), studentId).orElse(null);
    } else if (admission != null) {
      entity = marks.findByExamDefinitionIdAndAdmissionNo(exam.getId(), admission).orElse(null);
    }
    BigDecimal previousMarks = entity == null ? null : entity.getMarksObtained();
    String previousStatus = entity == null ? null : entity.getEntryStatus();
    if (entity == null) {
      entity = new ExamMarkEntity();
      entity.setId(UUID.randomUUID());
      entity.setOrganizationId(scope.organizationId());
      entity.setExamDefinitionId(exam.getId());
      entity.setCreatedAt(Instant.now());
    }
    entity.setStudentId(studentId);
    entity.setAdmissionNo(admission);
    entity.setStudentName(asString(mark.get("studentName")));
    entity.setRollNo(asString(mark.get("rollNo")));
    entity.setMarksObtained(obtained);
    entity.setEntryStatus(status);
    entity.setRemarks(asString(mark.get("remarks")));
    Map<String, Object> components = new LinkedHashMap<>();
    if (obtained != null) {
      components.put("marks", obtained);
    }
    entity.setComponentMarks(components);
    List<Map<String, Object>> bands =
        grading == null ? GradingSupport.defaultBands() : grading.bandsFor(scope);
    if (bands == null || bands.isEmpty()) {
      bands = GradingSupport.defaultBands();
    }
    Map<String, Object> assessed = GradingSupport.apply(GradingSupport.percentage(obtained, exam.getMaxMarks()), bands);
    entity.setGrade(obtained == null ? null : asString(assessed.get("grade")));
    entity.setUpdatedBy(scope.userId());
    entity.setUpdatedAt(Instant.now());
    marks.save(entity);
    boolean changed =
        !sameDecimal(previousMarks, obtained) || !sameText(previousStatus, status);
    if (changed && (previousMarks != null || previousStatus != null || obtained != null || status != null)) {
      writeAudit(
          exam,
          scope,
          studentId,
          admission,
          entity.getStudentName(),
          previousMarks,
          obtained,
          previousStatus,
          status,
          reason);
    }
  }

  private void writeAudit(
      ExamDefinitionEntity exam,
      TenantScope scope,
      UUID studentId,
      String admission,
      String studentName,
      BigDecimal previousMarks,
      BigDecimal newMarks,
      String previousStatus,
      String newStatus,
      String reason) {
    if (audits == null) {
      return;
    }
    ExamMarkAuditEntity row = new ExamMarkAuditEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(scope.organizationId());
    row.setExamDefinitionId(exam.getId());
    row.setStudentId(studentId);
    row.setAdmissionNo(admission);
    row.setStudentName(studentName);
    row.setPreviousMarks(previousMarks);
    row.setNewMarks(newMarks);
    row.setPreviousStatus(previousStatus);
    row.setNewStatus(newStatus);
    row.setReason(reason);
    row.setChangedBy(scope.userId());
    row.setChangedAt(Instant.now());
    audits.save(row);
  }

  private void assertWritable(ExamDefinitionEntity exam) {
    if ("LOCKED".equals(exam.getStatus())) {
      throw new ExamException("LOCKED", "Exam is locked");
    }
    if ("PUBLISHED".equals(exam.getStatus())) {
      throw new ExamException("PUBLISHED", "Published exams are read-only");
    }
    if ("SUBMITTED".equals(exam.getStatus()) || "CORRECTION_REQUESTED".equals(exam.getStatus())) {
      throw new ExamException("SUBMITTED", "Submitted marks are read-only until they are unlocked");
    }
  }

  private void assertFresh(ExamDefinitionEntity exam, Object expected) {
    String stamp = asString(expected);
    if (stamp == null || exam.getUpdatedAt() == null) {
      return;
    }
    if (!stamp.equals(exam.getUpdatedAt().toString())) {
      throw new ExamException(
          "CONFLICT", "Another teacher saved these marks. Reload and try again.");
    }
  }

  private ExamDefinitionEntity requireDefinition(UUID id, String org) {
    return definitions
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new ExamException("NOT_FOUND", "Exam definition not found"));
  }

  private void requireSubjectAccess(TenantScope scope, UUID sectionId, UUID subjectId) {
    if (!PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      return;
    }
    if (!PersonaRoles.isTeacher(scope.roleCode())) {
      throw new SecurityException("Only staff/teachers can manage gradebook for a section");
    }
    Map<String, Object> teacherScope = academic.teacherScope(scope);
    if (!teacherOwnsSubject(teacherScope, sectionId, subjectId)) {
      throw new SecurityException("Teacher is not assigned to this class and subject");
    }
  }

  private boolean canUnlock(TenantScope scope, UUID sectionId) {
    if (PersonaRoles.isElevated(scope.roleCode())) {
      return true;
    }
    if (!PersonaRoles.isTeacher(scope.roleCode())) {
      return false;
    }
    Map<String, Object> teacherScope = academic.teacherScope(scope);
    return idListContains(teacherScope.get("classTeacherSectionIds"), sectionId);
  }

  static boolean teacherOwnsSubject(Map<String, Object> teacherScope, UUID sectionId, UUID subjectId) {
    if (teacherScope == null || sectionId == null) {
      return false;
    }
    if (idListContains(teacherScope.get("classTeacherSectionIds"), sectionId)) {
      return true;
    }
    Object assignments = teacherScope.get("assignments");
    boolean sawAssignments = assignments instanceof List<?> list && !list.isEmpty();
    if (assignments instanceof List<?> list) {
      for (Object item : list) {
        if (!(item instanceof Map<?, ?> row)) {
          continue;
        }
        if (!sectionId.toString().equalsIgnoreCase(String.valueOf(row.get("sectionId")))) {
          continue;
        }
        Object subject = row.get("subjectId");
        if (subject == null || "null".equalsIgnoreCase(String.valueOf(subject)) || String.valueOf(subject).isBlank()) {
          return true;
        }
        if (subjectId != null && subjectId.toString().equalsIgnoreCase(String.valueOf(subject))) {
          return true;
        }
      }
    }
    if (!sawAssignments && idListContains(teacherScope.get("sectionIds"), sectionId)) {
      return true;
    }
    return false;
  }

  private static boolean idListContains(Object ids, UUID sectionId) {
    if (sectionId == null || !(ids instanceof List<?> list)) {
      return false;
    }
    for (Object id : list) {
      if (sectionId.toString().equalsIgnoreCase(String.valueOf(id))) {
        return true;
      }
    }
    return false;
  }

  private List<ExamDefinitionEntity> filterSession(List<ExamDefinitionEntity> found, TenantScope scope) {
    String session = scope.academicSessionId();
    String branch = scope.branchId();
    return found.stream()
        .filter(
            d ->
                session == null
                    || session.isBlank()
                    || d.getAcademicSessionId() == null
                    || d.getAcademicSessionId().isBlank()
                    || session.equals(d.getAcademicSessionId()))
        .filter(
            d ->
                branch == null
                    || branch.isBlank()
                    || d.getBranchId() == null
                    || d.getBranchId().isBlank()
                    || branch.equals(d.getBranchId()))
        .toList();
  }

  private Map<String, Object> studentRow(
      Map<String, Object> row,
      ExamMarkEntity existing,
      ExamDefinitionEntity exam,
      ExamEntryPolicyEntity policy,
      List<Map<String, Object>> bands,
      boolean leftSection) {
    UUID studentId = parseUuid(row.get("id"));
    String admission = firstNonBlank(asString(row.get("admissionNo")), existing == null ? null : existing.getAdmissionNo());
    BigDecimal obtained = existing == null ? null : existing.getMarksObtained();
    String status = existing == null ? null : existing.getEntryStatus();
    Map<String, Object> assessed =
        GradingSupport.apply(GradingSupport.percentage(obtained, exam.getMaxMarks()), bands);
    boolean pass = Boolean.TRUE.equals(assessed.get("pass"));
    if (exam.getPassingMarks() != null && obtained != null) {
      pass = obtained.compareTo(exam.getPassingMarks()) >= 0;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("studentId", studentId == null ? null : studentId.toString());
    out.put("admissionNo", admission);
    out.put(
        "studentName",
        firstNonBlank(
            asString(row.get("fullName")),
            asString(row.get("studentName")),
            existing == null ? null : existing.getStudentName(),
            admission));
    out.put("rollNo", firstNonBlank(asString(row.get("rollNo")), existing == null ? null : existing.getRollNo()));
    out.put("marksObtained", obtained);
    out.put("entryStatus", status);
    out.put("remarks", existing == null ? null : existing.getRemarks());
    out.put("grade", obtained == null ? null : assessed.get("grade"));
    out.put("percentage", obtained == null ? null : assessed.get("percentage"));
    out.put("gradePoint", obtained == null ? null : assessed.get("gradePoint"));
    out.put("result", obtained == null ? null : (pass ? "PASS" : "FAIL"));
    out.put("leftSection", leftSection);
    out.put("complete", isComplete(obtained, status, policy));
    return out;
  }

  private static boolean isComplete(BigDecimal marks, String status, ExamEntryPolicyEntity policy) {
    if (status != null && EXCUSED.contains(status)) {
      return true;
    }
    return marks != null;
  }

  private int[] countProgress(
      List<Map<String, Object>> roster, List<ExamMarkEntity> saved, ExamEntryPolicyEntity policy) {
    Map<UUID, ExamMarkEntity> byStudent = new LinkedHashMap<>();
    Map<String, ExamMarkEntity> byAdmission = new LinkedHashMap<>();
    for (ExamMarkEntity m : saved) {
      if (m.getStudentId() != null) {
        byStudent.put(m.getStudentId(), m);
      }
      if (m.getAdmissionNo() != null) {
        byAdmission.put(m.getAdmissionNo().toLowerCase(Locale.ROOT), m);
      }
    }
    int total = 0;
    int completed = 0;
    if (roster == null || roster.isEmpty()) {
      total = saved.size();
      for (ExamMarkEntity m : saved) {
        if (isComplete(m.getMarksObtained(), m.getEntryStatus(), policy)) {
          completed++;
        }
      }
    } else {
      total = roster.size();
      for (Map<String, Object> row : roster) {
        ExamMarkEntity existing =
            matchMark(byStudent, byAdmission, parseUuid(row.get("id")), asString(row.get("admissionNo")));
        if (existing != null && isComplete(existing.getMarksObtained(), existing.getEntryStatus(), policy)) {
          completed++;
        }
      }
    }
    return new int[] {total, completed, Math.max(0, total - completed)};
  }

  private Map<String, Object> progressMap(List<Map<String, Object>> students) {
    int total = students.size();
    int completed = 0;
    int absent = 0;
    int notAppeared = 0;
    int exempted = 0;
    int medical = 0;
    int withheld = 0;
    int present = 0;
    for (Map<String, Object> s : students) {
      if (Boolean.TRUE.equals(s.get("complete"))) {
        completed++;
      }
      String status = asString(s.get("entryStatus"));
      if ("ABSENT".equals(status)) absent++;
      else if ("NOT_APPEARED".equals(status)) notAppeared++;
      else if ("EXEMPTED".equals(status)) exempted++;
      else if ("MEDICAL_LEAVE".equals(status)) medical++;
      else if ("WITHHELD".equals(status)) withheld++;
      else if ("PRESENT".equals(status) || s.get("marksObtained") != null) present++;
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("total", total);
    m.put("completed", completed);
    m.put("pending", Math.max(0, total - completed));
    m.put("entered", present);
    m.put("absent", absent);
    m.put("notAppeared", notAppeared);
    m.put("exempted", exempted);
    m.put("medicalLeave", medical);
    m.put("withheld", withheld);
    m.put("percent", total == 0 ? 0 : (completed * 100) / total);
    return m;
  }

  private Map<String, Object> summaryMap(List<Map<String, Object>> students, ExamDefinitionEntity exam) {
    BigDecimal sum = BigDecimal.ZERO;
    int n = 0;
    BigDecimal high = null;
    BigDecimal low = null;
    for (Map<String, Object> s : students) {
      if (!(s.get("marksObtained") instanceof BigDecimal marks)) {
        continue;
      }
      if (EXCUSED.contains(String.valueOf(s.get("entryStatus")))) {
        continue;
      }
      sum = sum.add(marks);
      n++;
      high = high == null || marks.compareTo(high) > 0 ? marks : high;
      low = low == null || marks.compareTo(low) < 0 ? marks : low;
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("count", n);
    m.put(
        "average",
        n == 0 ? null : sum.divide(BigDecimal.valueOf(n), 2, java.math.RoundingMode.HALF_UP));
    m.put("highest", high);
    m.put("lowest", low);
    m.put("maxMarks", exam.getMaxMarks());
    return m;
  }

  private ExamEntryPolicyEntity policy(TenantScope scope) {
    if (policies == null) {
      return new ExamEntryPolicyEntity();
    }
    return policies
        .findByOrganizationId(scope.organizationId())
        .orElseGet(ExamEntryPolicyEntity::new);
  }

  private static Map<String, Object> policyMap(ExamEntryPolicyEntity policy) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("absentAsZero", policy.isAbsentAsZero());
    m.put("allowIncompleteSubmit", policy.isAllowIncompleteSubmit());
    m.put("allowMarksWhenExcused", policy.isAllowMarksWhenExcused());
    return m;
  }

  private Map<String, Object> definitionToMap(ExamDefinitionEntity e, TenantScope scope) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId().toString());
    m.put("name", e.getName());
    m.put("termKey", e.getTermKey());
    m.put("subjectId", e.getSubjectId().toString());
    m.put("sectionId", e.getSectionId().toString());
    m.put("maxMarks", e.getMaxMarks());
    m.put("passingMarks", e.getPassingMarks());
    m.put("examDate", e.getExamDate() == null ? null : e.getExamDate().toString());
    m.put("components", effectiveComponents(e));
    m.put("status", e.getStatus());
    m.put("createdBy", e.getCreatedBy());
    m.put("academicSessionId", e.getAcademicSessionId());
    m.put("branchId", e.getBranchId());
    m.put("publishedAt", e.getPublishedAt() == null ? null : e.getPublishedAt().toString());
    m.put("lockedAt", e.getLockedAt() == null ? null : e.getLockedAt().toString());
    m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
    return m;
  }

  private static List<Map<String, Object>> effectiveComponents(ExamDefinitionEntity e) {
    if (e.getComponents() != null && !e.getComponents().isEmpty()) {
      return e.getComponents();
    }
    Map<String, Object> one = new LinkedHashMap<>();
    one.put("key", "marks");
    one.put("label", "Marks");
    one.put("maxMarks", e.getMaxMarks());
    return List.of(one);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> componentsFrom(Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return new ArrayList<>();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        out.add(new LinkedHashMap<>((Map<String, Object>) map));
      }
    }
    return out;
  }

  private static Map<String, Object> markToMap(ExamMarkEntity m, ExamDefinitionEntity exam) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", m.getId().toString());
    out.put("examDefinitionId", exam.getId().toString());
    out.put("examName", exam.getName());
    out.put("termKey", exam.getTermKey());
    out.put("maxMarks", exam.getMaxMarks());
    out.put("studentId", m.getStudentId() == null ? null : m.getStudentId().toString());
    out.put("admissionNo", m.getAdmissionNo());
    out.put("studentName", m.getStudentName());
    out.put("marksObtained", m.getMarksObtained());
    out.put("grade", m.getGrade());
    out.put("entryStatus", m.getEntryStatus());
    out.put("status", exam.getStatus());
    return out;
  }

  private static ExamMarkEntity matchMark(
      Map<UUID, ExamMarkEntity> byStudent,
      Map<String, ExamMarkEntity> byAdmission,
      UUID studentId,
      String admission) {
    if (studentId != null && byStudent.containsKey(studentId)) {
      return byStudent.get(studentId);
    }
    if (admission == null) {
      return null;
    }
    return byAdmission.get(admission.toLowerCase(Locale.ROOT));
  }

  private Map<String, String> classNames(TenantScope scope) {
    Map<String, String> out = new LinkedHashMap<>();
    for (Map<String, Object> c : academic.listClasses(scope)) {
      String id = asString(c.get("id"));
      if (id != null) {
        out.put(id, firstNonBlank(asString(c.get("name")), asString(c.get("code")), "Class"));
      }
    }
    return out;
  }

  private Map<String, String> subjectNames(TenantScope scope) {
    Map<String, String> out = new LinkedHashMap<>();
    for (Map<String, Object> s : academic.listSubjects(scope)) {
      String id = asString(s.get("id"));
      if (id != null) {
        out.put(id, firstNonBlank(asString(s.get("name")), asString(s.get("code")), "Subject"));
      }
    }
    return out;
  }

  private Map<String, Map<String, Object>> sectionIndex(TenantScope scope) {
    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    for (Map<String, Object> s : academic.listSections(scope)) {
      String id = asString(s.get("id"));
      if (id != null) {
        out.put(id, s);
      }
    }
    return out;
  }

  private Map<String, String> assignmentTeachers(TenantScope scope) {
    Map<String, String> out = new LinkedHashMap<>();
    for (Map<String, Object> a : academic.listAssignments(scope)) {
      String sectionId = asString(a.get("sectionId"));
      String subjectId = asString(a.get("subjectId"));
      String teacher = asString(a.get("teacherUsername"));
      if (sectionId != null && subjectId != null && teacher != null) {
        out.put(sectionId + "|" + subjectId, teacher);
      }
    }
    return out;
  }

  private UUID subjectIdByName(TenantScope scope, String name) {
    for (Map<String, Object> s : academic.listSubjects(scope)) {
      if (name.equalsIgnoreCase(asString(s.get("name")))) {
        return parseUuid(s.get("id"));
      }
    }
    return null;
  }

  private UUID sectionIdByLabel(TenantScope scope, String label) {
    if (label == null) {
      return null;
    }
    String want = label.trim().toLowerCase(Locale.ROOT);
    for (Map<String, Object> s : academic.listSections(scope)) {
      for (String key : List.of("studentLabel", "name", "code")) {
        String value = asString(s.get(key));
        if (value != null && value.equalsIgnoreCase(want)) {
          return parseUuid(s.get("id"));
        }
      }
    }
    return null;
  }

  private static String studentKey(Map<String, Object> mark) {
    String id = asString(mark.get("studentId"));
    if (id != null) {
      return "id:" + id.toLowerCase(Locale.ROOT);
    }
    String admission = asString(mark.get("admissionNo"));
    return admission == null ? null : "adm:" + admission.toLowerCase(Locale.ROOT);
  }

  private static String normalizeStatus(String status) {
    if (status == null) {
      return null;
    }
    String value = status.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    return switch (value) {
      case "PRESENT", "ABSENT", "NOT_APPEARED", "EXEMPTED", "MEDICAL_LEAVE", "WITHHELD" -> value;
      default -> throw new ExamException("VALIDATION", "Unknown marks status: " + status);
    };
  }

  private static boolean sameDecimal(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == null && b == null;
    }
    return a.compareTo(b) == 0;
  }

  private static boolean sameText(String a, String b) {
    if (a == null || a.isBlank()) {
      return b == null || b.isBlank();
    }
    return a.equals(b);
  }

  private static boolean isBlank(Object raw) {
    return raw == null || String.valueOf(raw).trim().isEmpty() || "null".equalsIgnoreCase(String.valueOf(raw).trim());
  }

  private static UUID parseUuid(Object raw) {
    if (raw == null) return null;
    String s = String.valueOf(raw).trim();
    if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static BigDecimal toDecimal(Object raw) {
    BigDecimal v = toDecimalNullable(raw);
    return v == null ? BigDecimal.ZERO : v;
  }

  private static BigDecimal toDecimalNullable(Object raw) {
    if (raw == null) return null;
    if (raw instanceof BigDecimal decimal) return decimal;
    if (raw instanceof Number number) return new BigDecimal(number.toString());
    String s = String.valueOf(raw).trim();
    if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
    try {
      return new BigDecimal(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static LocalDate parseDate(Object raw) {
    String s = asString(raw);
    if (s == null) return null;
    try {
      return LocalDate.parse(s);
    } catch (Exception ex) {
      return null;
    }
  }

  private static String asString(Object v) {
    if (v == null) return null;
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }
}
