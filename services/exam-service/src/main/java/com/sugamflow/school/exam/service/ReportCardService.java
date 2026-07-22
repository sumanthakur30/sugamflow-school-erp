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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles term report cards from published (or all, for staff) gradebook definitions: aggregates
 * each subject's marks for the term, computes totals, percentage and an overall grade band.
 */
@Service
public class ReportCardService {

  private final ExamDefinitionRepository definitions;
  private final ExamMarkRepository marks;
  private final AcademicClient academic;
  private final StudentDirectoryClient directory;
  private final StudentAccessClient studentAccess;

  public ReportCardService(
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

  /** Staff view: full section report card grid for a term. */
  @Transactional(readOnly = true)
  public Map<String, Object> sectionTermReport(UUID sectionId, String termKey, boolean publishedOnly) {
    TenantScope scope = TenantContext.require();
    if (sectionId == null || termKey == null || termKey.isBlank()) {
      throw new ExamException("VALIDATION", "sectionId and termKey are required");
    }
    requireSectionAccess(scope, sectionId);
    return buildSectionReport(scope, sectionId, termKey.trim().toUpperCase(Locale.ROOT), publishedOnly, null);
  }

  /** Staff view: single student's report card for a term. */
  @Transactional(readOnly = true)
  public Map<String, Object> studentTermReport(
      UUID sectionId, String termKey, String studentKey, boolean publishedOnly) {
    TenantScope scope = TenantContext.require();
    if (sectionId == null || termKey == null || termKey.isBlank() || studentKey == null) {
      throw new ExamException("VALIDATION", "sectionId, termKey and student are required");
    }
    requireSectionAccess(scope, sectionId);
    Map<String, Object> card =
        oneStudent(
            buildSectionReport(
                scope, sectionId, termKey.trim().toUpperCase(Locale.ROOT), publishedOnly, studentKey),
            studentKey);
    if (card == null) {
      throw new ExamException("NOT_FOUND", "No report card for the requested student in this term");
    }
    return card;
  }

  /**
   * Parent/student view: published report cards for the caller's linked children in a term. When
   * termKey is blank, every published term the child has is returned.
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> myReportCards(String termKey) {
    TenantScope scope = TenantContext.require();
    AccessScope access = studentAccess.resolve(scope);
    if (!access.restricted()) {
      // Elevated staff should use the section report; keep this endpoint child-scoped.
      return List.of();
    }
    if (access.studentIds().isEmpty()
        && access.admissionNos().isEmpty()
        && access.classSections().isEmpty()) {
      return List.of();
    }
    String term = termKey == null || termKey.isBlank() ? null : termKey.trim().toUpperCase(Locale.ROOT);

    List<ExamDefinitionEntity> published =
        definitions.findByOrganizationIdAndStatusOrderByUpdatedAtDesc(
            scope.organizationId(), "PUBLISHED");
    // Distinct (sectionId, termKey) the child is part of.
    Set<String> keys = new LinkedHashSet<>();
    for (ExamDefinitionEntity d : published) {
      if (term != null && !term.equals(d.getTermKey())) {
        continue;
      }
      keys.add(d.getSectionId() + "|" + d.getTermKey());
    }

    List<Map<String, Object>> cards = new ArrayList<>();
    for (String key : keys) {
      int sep = key.lastIndexOf('|');
      UUID sectionId = parseUuid(key.substring(0, sep));
      String cardTerm = key.substring(sep + 1);
      if (sectionId == null) {
        continue;
      }
      Map<String, Object> section = buildSectionReport(scope, sectionId, cardTerm, true, null);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> students = (List<Map<String, Object>>) section.get("students");
      for (Map<String, Object> student : students) {
        if (access.allowsStudentDto(student)) {
          Map<String, Object> card = new LinkedHashMap<>(section);
          card.remove("students");
          card.put("student", student);
          cards.add(card);
        }
      }
    }
    return cards;
  }

  /** Resolve a single restricted (parent/student) child card, enforcing access. */
  @Transactional(readOnly = true)
  public Map<String, Object> myStudentTermReport(UUID sectionId, String termKey, String studentKey) {
    TenantScope scope = TenantContext.require();
    AccessScope access = studentAccess.resolve(scope);
    if (sectionId == null || termKey == null || termKey.isBlank()) {
      throw new ExamException("VALIDATION", "sectionId and termKey are required");
    }
    Map<String, Object> section =
        buildSectionReport(scope, sectionId, termKey.trim().toUpperCase(Locale.ROOT), true, studentKey);
    Map<String, Object> card = oneStudent(section, studentKey);
    if (card == null || (access.restricted() && !access.allowsStudentDto(card))) {
      throw new ExamException("NOT_FOUND", "No published report card for this student in the term");
    }
    return card;
  }

  private Map<String, Object> buildSectionReport(
      TenantScope scope, UUID sectionId, String termKey, boolean publishedOnly, String onlyStudentKey) {
    List<ExamDefinitionEntity> defs =
        publishedOnly
            ? definitions.findByOrganizationIdAndSectionIdAndTermKeyAndStatusOrderByNameAsc(
                scope.organizationId(), sectionId, termKey, "PUBLISHED")
            : definitions.findByOrganizationIdAndSectionIdAndTermKeyOrderByNameAsc(
                scope.organizationId(), sectionId, termKey);

    Map<String, Object> section = academic.getSection(scope, sectionId.toString());
    String label =
        firstNonBlank(
            asString(section.get("studentLabel")),
            asString(section.get("name")),
            sectionId.toString());

    Map<String, String> subjectNames = subjectNames(scope);

    // subjectId -> ordered list of definitions in this term
    Map<String, List<ExamDefinitionEntity>> bySubject = new LinkedHashMap<>();
    Map<String, List<ExamMarkEntity>> marksByDef = new LinkedHashMap<>();
    for (ExamDefinitionEntity d : defs) {
      bySubject.computeIfAbsent(d.getSubjectId().toString(), k -> new ArrayList<>()).add(d);
      marksByDef.put(d.getId().toString(), marks.findByExamDefinitionIdOrderByStudentNameAsc(d.getId()));
    }

    List<Map<String, Object>> subjectColumns = new ArrayList<>();
    for (String subjectId : bySubject.keySet()) {
      Map<String, Object> col = new LinkedHashMap<>();
      col.put("subjectId", subjectId);
      col.put("name", subjectNames.getOrDefault(subjectId, "Subject"));
      subjectColumns.add(col);
    }

    List<Map<String, Object>> students = new ArrayList<>();
    for (Map<String, Object> row : directory.listByClassSection(scope, label, 500)) {
      UUID studentId = parseUuid(row.get("id"));
      String admission = asString(row.get("admissionNo"));
      String studentName =
          firstNonBlank(asString(row.get("fullName")), asString(row.get("studentName")), admission);
      if (onlyStudentKey != null && !matchesStudent(onlyStudentKey, studentId, admission)) {
        continue;
      }

      List<Map<String, Object>> subjectRows = new ArrayList<>();
      BigDecimal totalObtained = BigDecimal.ZERO;
      BigDecimal totalMax = BigDecimal.ZERO;
      int attempted = 0;
      for (String subjectId : bySubject.keySet()) {
        BigDecimal obtained = null;
        BigDecimal max = BigDecimal.ZERO;
        boolean hasMark = false;
        for (ExamDefinitionEntity d : bySubject.get(subjectId)) {
          ExamMarkEntity mark = findMark(marksByDef.get(d.getId().toString()), studentId, admission);
          if (mark != null && mark.getMarksObtained() != null) {
            obtained = (obtained == null ? BigDecimal.ZERO : obtained).add(mark.getMarksObtained());
            max = max.add(d.getMaxMarks() == null ? BigDecimal.ZERO : d.getMaxMarks());
            hasMark = true;
          }
        }
        Map<String, Object> sr = new LinkedHashMap<>();
        sr.put("subjectId", subjectId);
        sr.put("name", subjectNames.getOrDefault(subjectId, "Subject"));
        sr.put("marksObtained", obtained);
        sr.put("maxMarks", hasMark ? max : null);
        sr.put("grade", hasMark ? gradeBand(percentage(obtained, max)) : null);
        subjectRows.add(sr);
        if (hasMark) {
          totalObtained = totalObtained.add(obtained);
          totalMax = totalMax.add(max);
          attempted++;
        }
      }

      BigDecimal pct = percentage(totalObtained, totalMax);
      Map<String, Object> student = new LinkedHashMap<>();
      student.put("studentId", studentId == null ? null : studentId.toString());
      student.put("admissionNo", admission);
      student.put("studentName", studentName);
      student.put("classSection", firstNonBlank(asString(row.get("classSection")), label));
      student.put("subjects", subjectRows);
      student.put("subjectsAttempted", attempted);
      student.put("totalObtained", attempted > 0 ? totalObtained : null);
      student.put("totalMax", attempted > 0 ? totalMax : null);
      student.put("percentage", attempted > 0 ? pct : null);
      student.put("overallGrade", attempted > 0 ? gradeBand(pct) : null);
      student.put("result", attempted > 0 ? (pct.compareTo(new BigDecimal("33")) >= 0 ? "PASS" : "FAIL") : null);
      students.add(student);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sectionId", sectionId.toString());
    result.put("sectionLabel", label);
    result.put("termKey", termKey);
    result.put("publishedOnly", publishedOnly);
    result.put("subjects", subjectColumns);
    result.put("students", students);
    return result;
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

  private static Map<String, Object> oneStudent(Map<String, Object> section, String studentKey) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> students = (List<Map<String, Object>>) section.get("students");
    for (Map<String, Object> s : students) {
      if (matchesStudent(studentKey, parseUuid(s.get("studentId")), asString(s.get("admissionNo")))) {
        Map<String, Object> card = new LinkedHashMap<>(section);
        card.remove("students");
        card.put("student", s);
        return card;
      }
    }
    return null;
  }

  private static boolean matchesStudent(String key, UUID studentId, String admission) {
    if (key == null) {
      return false;
    }
    String k = key.trim();
    if (studentId != null && k.equalsIgnoreCase(studentId.toString())) {
      return true;
    }
    return admission != null && k.equalsIgnoreCase(admission);
  }

  private static ExamMarkEntity findMark(List<ExamMarkEntity> list, UUID studentId, String admission) {
    if (list == null) {
      return null;
    }
    for (ExamMarkEntity m : list) {
      if (studentId != null && studentId.equals(m.getStudentId())) {
        return m;
      }
      if (admission != null
          && m.getAdmissionNo() != null
          && admission.equalsIgnoreCase(m.getAdmissionNo())) {
        return m;
      }
    }
    return null;
  }

  private static BigDecimal percentage(BigDecimal obtained, BigDecimal max) {
    if (obtained == null || max == null || max.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    return obtained.multiply(new BigDecimal("100")).divide(max, 2, RoundingMode.HALF_UP);
  }

  private static String gradeBand(BigDecimal pct) {
    if (pct == null) {
      return null;
    }
    double p = pct.doubleValue();
    if (p >= 90) return "A+";
    if (p >= 80) return "A";
    if (p >= 70) return "B+";
    if (p >= 60) return "B";
    if (p >= 50) return "C";
    if (p >= 40) return "D";
    if (p >= 33) return "E";
    return "F";
  }

  private void requireSectionAccess(TenantScope scope, UUID sectionId) {
    if (!PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      return;
    }
    if (!PersonaRoles.isTeacher(scope.roleCode())) {
      throw new SecurityException("Only staff/teachers can view section report cards");
    }
    Map<String, Object> teacherScope = academic.teacherScope(scope);
    Object ids = teacherScope.get("sectionIds");
    if (ids instanceof List<?> list) {
      for (Object id : list) {
        if (sectionId.toString().equalsIgnoreCase(String.valueOf(id))) {
          return;
        }
      }
    }
    throw new SecurityException("Teacher is not assigned to section " + sectionId);
  }

  private static UUID parseUuid(Object raw) {
    if (raw == null) {
      return null;
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
      return null;
    }
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static String asString(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
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
