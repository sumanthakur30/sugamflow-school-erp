package com.sugamflow.school.exam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.integration.AcademicClient;
import com.sugamflow.school.exam.integration.AttendanceSummaryClient;
import com.sugamflow.school.exam.integration.StudentAccessClient;
import com.sugamflow.school.exam.integration.StudentDirectoryClient;
import com.sugamflow.school.exam.persistence.entity.ExamDefinitionEntity;
import com.sugamflow.school.exam.persistence.entity.ExamMarkEntity;
import com.sugamflow.school.exam.persistence.repo.ExamDefinitionRepository;
import com.sugamflow.school.exam.persistence.repo.ExamMarkRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportCardServiceTest {

  @Mock private ExamDefinitionRepository definitions;
  @Mock private ExamMarkRepository marks;
  @Mock private AcademicClient academic;
  @Mock private StudentDirectoryClient directory;
  @Mock private StudentAccessClient studentAccess;
  @Mock private GradingSupport grading;
  @Mock private AttendanceSummaryClient attendance;
  @InjectMocks private ReportCardService service;

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private ExamDefinitionEntity def(UUID id, UUID sectionId, UUID subjectId, String max) {
    ExamDefinitionEntity e = new ExamDefinitionEntity();
    e.setId(id);
    e.setOrganizationId("demo-school");
    e.setSectionId(sectionId);
    e.setSubjectId(subjectId);
    e.setTermKey("TERM1");
    e.setName("UT1");
    e.setMaxMarks(new BigDecimal(max));
    e.setStatus("PUBLISHED");
    return e;
  }

  @Test
  void sectionReportAggregatesSubjectsAndComputesTotals() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID sectionId = UUID.randomUUID();
    UUID mathId = UUID.randomUUID();
    UUID sciId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID mathDef = UUID.randomUUID();
    UUID sciDef = UUID.randomUUID();

    when(academic.getSection(any(), eq(sectionId.toString())))
        .thenReturn(Map.of("studentLabel", "Grade 8-A"));
    when(academic.listSubjects(any()))
        .thenReturn(
            List.of(
                Map.of("id", mathId.toString(), "name", "Mathematics"),
                Map.of("id", sciId.toString(), "name", "Science")));
    when(definitions.findByOrganizationIdAndSectionIdAndTermKeyAndStatusOrderByNameAsc(
            "demo-school", sectionId, "TERM1", "PUBLISHED"))
        .thenReturn(List.of(def(mathDef, sectionId, mathId, "100"), def(sciDef, sectionId, sciId, "50")));

    ExamMarkEntity mathMark = new ExamMarkEntity();
    mathMark.setStudentId(studentId);
    mathMark.setMarksObtained(new BigDecimal("80"));
    ExamMarkEntity sciMark = new ExamMarkEntity();
    sciMark.setStudentId(studentId);
    sciMark.setMarksObtained(new BigDecimal("45"));
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(mathDef)).thenReturn(List.of(mathMark));
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(sciDef)).thenReturn(List.of(sciMark));

    when(directory.listByClassSection(any(), eq("Grade 8-A"), anyInt()))
        .thenReturn(
            List.of(
                Map.of("id", studentId.toString(), "admissionNo", "ADM-1", "fullName", "Asha")));

    Map<String, Object> report = service.sectionTermReport(sectionId, "term1", true);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> students = (List<Map<String, Object>>) report.get("students");
    assertEquals(1, students.size());
    Map<String, Object> student = students.get(0);
    // 125 / 150 = 83.33% -> grade A
    assertEquals(new BigDecimal("125"), student.get("totalObtained"));
    assertEquals(new BigDecimal("150"), student.get("totalMax"));
    assertEquals("A", student.get("overallGrade"));
    assertEquals("PASS", student.get("result"));
  }

  @Test
  void studentTermReportReturnsSingleCard() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID sectionId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID defId = UUID.randomUUID();

    when(academic.getSection(any(), eq(sectionId.toString())))
        .thenReturn(Map.of("studentLabel", "Grade 8-A"));
    when(academic.listSubjects(any())).thenReturn(List.of(Map.of("id", subjectId.toString(), "name", "Math")));
    when(definitions.findByOrganizationIdAndSectionIdAndTermKeyOrderByNameAsc(
            "demo-school", sectionId, "TERM1"))
        .thenReturn(List.of(def(defId, sectionId, subjectId, "100")));
    ExamMarkEntity mark = new ExamMarkEntity();
    mark.setAdmissionNo("ADM-1");
    mark.setMarksObtained(new BigDecimal("30"));
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(defId)).thenReturn(List.of(mark));
    when(directory.listByClassSection(any(), eq("Grade 8-A"), anyInt()))
        .thenReturn(List.of(Map.of("admissionNo", "ADM-1", "fullName", "Asha")));

    Map<String, Object> card = service.studentTermReport(sectionId, "TERM1", "ADM-1", false);
    assertNotNull(card.get("student"));
    assertNull(card.get("students"));
    @SuppressWarnings("unchecked")
    Map<String, Object> student = (Map<String, Object>) card.get("student");
    // 30/100 = 30% -> below 33 -> FAIL, grade F
    assertEquals("F", student.get("overallGrade"));
    assertEquals("FAIL", student.get("result"));
  }

  @Test
  void missingMarkIsListedAndASavedZeroStillCounts() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID sectionId = UUID.randomUUID();
    UUID mathId = UUID.randomUUID();
    UUID sciId = UUID.randomUUID();
    UUID ashaId = UUID.randomUUID();
    UUID rohanId = UUID.randomUUID();
    UUID mathDef = UUID.randomUUID();
    UUID sciDef = UUID.randomUUID();

    when(academic.getSection(any(), eq(sectionId.toString())))
        .thenReturn(Map.of("studentLabel", "Grade 8-A"));
    when(academic.listSubjects(any()))
        .thenReturn(
            List.of(
                Map.of("id", mathId.toString(), "name", "Mathematics"),
                Map.of("id", sciId.toString(), "name", "Science")));
    ExamDefinitionEntity math = def(mathDef, sectionId, mathId, "100");
    math.setName("Unit test");
    ExamDefinitionEntity science = def(sciDef, sectionId, sciId, "50");
    science.setName("Half yearly");
    when(definitions.findByOrganizationIdAndSectionIdAndTermKeyAndStatusOrderByNameAsc(
            "demo-school", sectionId, "TERM1", "PUBLISHED"))
        .thenReturn(List.of(math, science));

    ExamMarkEntity ashaMath = mark(ashaId, "0");
    ExamMarkEntity ashaScience = mark(ashaId, "40");
    ExamMarkEntity rohanMath = mark(rohanId, "0");
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(mathDef))
        .thenReturn(List.of(ashaMath, rohanMath));
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(sciDef)).thenReturn(List.of(ashaScience));
    when(directory.listByClassSection(any(), eq("Grade 8-A"), anyInt()))
        .thenReturn(
            List.of(
                Map.of("id", ashaId.toString(), "admissionNo", "ADM-1", "fullName", "Asha"),
                Map.of("id", rohanId.toString(), "admissionNo", "ADM-2", "fullName", "Rohan")));

    Map<String, Object> report = service.sectionTermReport(sectionId, "TERM1", true);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> students = (List<Map<String, Object>>) report.get("students");
    Map<String, Object> asha = studentNamed(students, "Asha");
    Map<String, Object> rohan = studentNamed(students, "Rohan");

    assertEquals(0, ((BigDecimal) subject(asha, "Mathematics").get("marksObtained")).compareTo(BigDecimal.ZERO));
    assertEquals(new BigDecimal("40"), subject(asha, "Science").get("marksObtained"));
    assertEquals(Boolean.TRUE, asha.get("complete"));

    assertEquals(0, ((BigDecimal) subject(rohan, "Mathematics").get("marksObtained")).compareTo(BigDecimal.ZERO));
    assertNull(subject(rohan, "Science").get("marksObtained"));
    assertEquals(Boolean.FALSE, rohan.get("complete"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> missing = (List<Map<String, Object>>) report.get("missingMarks");
    assertEquals(1, missing.size());
    assertEquals("Rohan", missing.get(0).get("studentName"));
    assertEquals("Science", missing.get(0).get("subject"));
    assertEquals("Half yearly", missing.get(0).get("exam"));
  }

  private static ExamMarkEntity mark(UUID studentId, String obtained) {
    ExamMarkEntity entity = new ExamMarkEntity();
    entity.setStudentId(studentId);
    entity.setMarksObtained(new BigDecimal(obtained));
    return entity;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> studentNamed(List<Map<String, Object>> students, String name) {
    return students.stream()
        .filter(row -> name.equals(row.get("studentName")))
        .findFirst()
        .orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> subject(Map<String, Object> student, String name) {
    List<Map<String, Object>> subjects = (List<Map<String, Object>>) student.get("subjects");
    return subjects.stream()
        .filter(row -> name.equals(row.get("name")))
        .findFirst()
        .orElseThrow();
  }
}
