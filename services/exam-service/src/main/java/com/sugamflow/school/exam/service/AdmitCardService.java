package com.sugamflow.school.exam.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.integration.AcademicClient;
import com.sugamflow.school.exam.integration.StudentDirectoryClient;
import com.sugamflow.school.exam.persistence.entity.ExamDefinitionEntity;
import com.sugamflow.school.exam.persistence.repo.ExamDefinitionRepository;
import com.sugamflow.school.exam.web.ExamException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Class admit cards built from the exams already scheduled for a section. */
@Service
public class AdmitCardService {

  private final ExamDefinitionRepository definitions;
  private final AcademicClient academic;
  private final StudentDirectoryClient directory;

  public AdmitCardService(
      ExamDefinitionRepository definitions,
      AcademicClient academic,
      StudentDirectoryClient directory) {
    this.definitions = definitions;
    this.academic = academic;
    this.directory = directory;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> pack(UUID sectionId, String termKey) {
    TenantScope scope = TenantContext.require();
    if (sectionId == null) {
      throw new ExamException("VALIDATION", "sectionId is required");
    }
    String term = termKey == null || termKey.isBlank() ? null : termKey.trim().toUpperCase(Locale.ROOT);
    List<ExamDefinitionEntity> defs =
        term == null
            ? definitions.findByOrganizationIdAndSectionIdOrderByUpdatedAtDesc(
                scope.organizationId(), sectionId)
            : definitions.findByOrganizationIdAndSectionIdAndTermKeyOrderByNameAsc(
                scope.organizationId(), sectionId, term);
    defs =
        defs.stream()
            .filter(d -> d.getStatus() == null || !"CANCELLED".equalsIgnoreCase(d.getStatus()))
            .sorted(
                Comparator.comparing(
                    ExamDefinitionEntity::getExamDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    if (defs.isEmpty()) {
      throw new ExamException("NOT_FOUND", "No exams are scheduled for this class");
    }

    Map<String, Object> section = academic.getSection(scope, sectionId.toString());
    String label =
        first(
            str(section.get("studentLabel")),
            str(section.get("name")),
            sectionId.toString());
    Map<String, String> subjects = new LinkedHashMap<>();
    for (Map<String, Object> s : academic.listSubjects(scope)) {
      String id = str(s.get("id"));
      if (id != null) {
        subjects.put(id, first(str(s.get("name")), str(s.get("code")), "Subject"));
      }
    }

    List<Map<String, Object>> papers = new ArrayList<>();
    for (ExamDefinitionEntity d : defs) {
      Map<String, Object> paper = new LinkedHashMap<>();
      paper.put("examId", d.getId().toString());
      paper.put("name", d.getName());
      paper.put(
          "subject",
          d.getSubjectId() == null
              ? "Subject"
              : subjects.getOrDefault(d.getSubjectId().toString(), "Subject"));
      paper.put("examDate", d.getExamDate() == null ? null : d.getExamDate().toString());
      paper.put("termKey", d.getTermKey());
      papers.add(paper);
    }

    List<Map<String, Object>> students = new ArrayList<>();
    for (Map<String, Object> row : directory.listByClassSection(scope, label, 500)) {
      Map<String, Object> student = new LinkedHashMap<>();
      student.put("studentId", str(row.get("id")));
      student.put("admissionNo", str(row.get("admissionNo")));
      student.put(
          "studentName",
          first(str(row.get("fullName")), str(row.get("studentName")), str(row.get("admissionNo"))));
      student.put("classSection", first(str(row.get("classSection")), label));
      students.add(student);
    }
    if (students.isEmpty()) {
      throw new ExamException("NOT_FOUND", "No students on this class roster");
    }

    Map<String, Object> pack = new LinkedHashMap<>();
    pack.put("sectionId", sectionId.toString());
    pack.put("sectionLabel", label);
    pack.put("termKey", term);
    pack.put("papers", papers);
    pack.put("students", students);
    return pack;
  }

  private static String str(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String first(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }
}
