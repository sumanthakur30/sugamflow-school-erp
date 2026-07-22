package com.sugamflow.school.exam.service;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.integration.AcademicClient;
import com.sugamflow.school.exam.integration.StudentAccessClient;
import com.sugamflow.school.exam.integration.StudentDirectoryClient;
import com.sugamflow.school.exam.persistence.entity.ExamDefinitionEntity;
import com.sugamflow.school.exam.persistence.entity.ExamMarkEntity;
import com.sugamflow.school.exam.persistence.repo.ExamDefinitionRepository;
import com.sugamflow.school.exam.persistence.repo.ExamMarkRepository;
import com.sugamflow.school.exam.web.ExamException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Teacher gradebook: exam definitions + section/subject marks grid with publish/lock. */
@Service
public class GradebookService {

  private final ExamDefinitionRepository definitions;
  private final ExamMarkRepository marks;
  private final AcademicClient academic;
  private final StudentDirectoryClient directory;
  private final StudentAccessClient studentAccess;

  public GradebookService(
      ExamDefinitionRepository definitions,
      ExamMarkRepository marks,
      AcademicClient academic,
      StudentDirectoryClient directory,
      StudentAccessClient studentAccess) {
    this.definitions = definitions;
    this.marks = marks;
    this.academic = academic;
    this.directory = directory;
    this.studentAccess = studentAccess;
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
    if (PersonaRoles.isTeacher(scope.roleCode())) {
      Map<String, Object> teacherScope = academic.teacherScope(scope);
      found = found.stream().filter(d -> teacherOwnsSection(teacherScope, d.getSectionId())).toList();
    }
    return found.stream().map(this::definitionToMap).toList();
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
    requireSectionAccess(scope, sectionId);

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
    e.setStatus("OPEN");
    e.setCreatedBy(scope.userId());
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    return definitionToMap(definitions.save(e));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> gradebook(UUID examDefinitionId) {
    TenantScope scope = TenantContext.require();
    ExamDefinitionEntity exam = requireDefinition(examDefinitionId, scope.organizationId());
    if (PersonaRoles.isRelationshipRestricted(scope.roleCode())
        && PersonaRoles.isTeacher(scope.roleCode())) {
      requireSectionAccess(scope, exam.getSectionId());
    }

    Map<String, Object> section = academic.getSection(scope, exam.getSectionId().toString());
    String label =
        firstNonBlank(
            asString(section.get("studentLabel")),
            asString(section.get("name")),
            exam.getSectionId().toString());

    Map<UUID, ExamMarkEntity> byStudent = new LinkedHashMap<>();
    Map<String, ExamMarkEntity> byAdmission = new LinkedHashMap<>();
    for (ExamMarkEntity m : marks.findByExamDefinitionIdOrderByStudentNameAsc(exam.getId())) {
      if (m.getStudentId() != null) {
        byStudent.put(m.getStudentId(), m);
      }
      if (m.getAdmissionNo() != null) {
        byAdmission.put(m.getAdmissionNo().toLowerCase(Locale.ROOT), m);
      }
    }

    List<Map<String, Object>> students = new ArrayList<>();
    for (Map<String, Object> row : directory.listByClassSection(scope, label, 500)) {
      UUID studentId = parseUuid(row.get("id"));
      String admission = asString(row.get("admissionNo"));
      ExamMarkEntity existing =
          studentId != null
              ? byStudent.get(studentId)
              : (admission == null ? null : byAdmission.get(admission.toLowerCase(Locale.ROOT)));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("studentId", studentId == null ? null : studentId.toString());
      out.put("admissionNo", admission);
      out.put(
          "studentName",
          firstNonBlank(asString(row.get("fullName")), asString(row.get("studentName")), admission));
      out.put("marksObtained", existing == null ? null : existing.getMarksObtained());
      out.put("grade", existing == null ? null : existing.getGrade());
      students.add(out);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("exam", definitionToMap(exam));
    result.put("sectionLabel", label);
    result.put("students", students);
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
    requireSectionAccess(scope, exam.getSectionId());
    if ("LOCKED".equals(exam.getStatus()) || "PUBLISHED".equals(exam.getStatus())) {
      // Allow edits on OPEN/DRAFT only; published can be reopened by elevated later.
      if ("LOCKED".equals(exam.getStatus())) {
        throw new ExamException("LOCKED", "Exam is locked");
      }
    }
    if ("PUBLISHED".equals(exam.getStatus()) && !PersonaRoles.isElevated(scope.roleCode())) {
      throw new ExamException("PUBLISHED", "Published exams are read-only for teachers");
    }

    Object raw = body.get("marks");
    if (!(raw instanceof List<?> list)) {
      throw new ExamException("VALIDATION", "marks array is required");
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> m)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> mark = (Map<String, Object>) m;
      upsertMark(exam, scope, mark);
    }
    if ("OPEN".equals(exam.getStatus()) || "DRAFT".equals(exam.getStatus())) {
      exam.setStatus("OPEN");
      exam.setUpdatedAt(Instant.now());
      definitions.save(exam);
    }
    return gradebook(exam.getId());
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
    requireSectionAccess(scope, exam.getSectionId());
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
    return definitionToMap(definitions.save(exam));
  }

  private void upsertMark(ExamDefinitionEntity exam, TenantScope scope, Map<String, Object> mark) {
    UUID studentId = parseUuid(mark.get("studentId"));
    String admission = asString(mark.get("admissionNo"));
    BigDecimal obtained = toDecimalNullable(mark.get("marksObtained"));
    if (obtained != null && obtained.compareTo(exam.getMaxMarks()) > 0) {
      throw new ExamException("VALIDATION", "marksObtained cannot exceed maxMarks");
    }
    if (obtained != null && obtained.compareTo(BigDecimal.ZERO) < 0) {
      throw new ExamException("VALIDATION", "marksObtained cannot be negative");
    }

    ExamMarkEntity entity = null;
    if (studentId != null) {
      entity = marks.findByExamDefinitionIdAndStudentId(exam.getId(), studentId).orElse(null);
    } else if (admission != null) {
      entity = marks.findByExamDefinitionIdAndAdmissionNo(exam.getId(), admission).orElse(null);
    }
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
    entity.setMarksObtained(obtained);
    entity.setGrade(asString(mark.get("grade")));
    entity.setUpdatedBy(scope.userId());
    entity.setUpdatedAt(Instant.now());
    marks.save(entity);
  }

  private ExamDefinitionEntity requireDefinition(UUID id, String org) {
    return definitions
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new ExamException("NOT_FOUND", "Exam definition not found"));
  }

  private void requireSectionAccess(TenantScope scope, UUID sectionId) {
    if (!PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      return;
    }
    if (!PersonaRoles.isTeacher(scope.roleCode())) {
      throw new SecurityException("Only staff/teachers can manage gradebook for a section");
    }
    Map<String, Object> teacherScope = academic.teacherScope(scope);
    if (!teacherOwnsSection(teacherScope, sectionId)) {
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

  private Map<String, Object> definitionToMap(ExamDefinitionEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId().toString());
    m.put("name", e.getName());
    m.put("termKey", e.getTermKey());
    m.put("subjectId", e.getSubjectId().toString());
    m.put("sectionId", e.getSectionId().toString());
    m.put("maxMarks", e.getMaxMarks());
    m.put("status", e.getStatus());
    m.put("publishedAt", e.getPublishedAt() == null ? null : e.getPublishedAt().toString());
    m.put("lockedAt", e.getLockedAt() == null ? null : e.getLockedAt().toString());
    m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
    return m;
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
    out.put("status", exam.getStatus());
    return out;
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
    String s = String.valueOf(raw).trim();
    if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
    try {
      return new BigDecimal(s);
    } catch (NumberFormatException ex) {
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
