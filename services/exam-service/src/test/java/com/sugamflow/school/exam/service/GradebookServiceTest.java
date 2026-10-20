package com.sugamflow.school.exam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.integration.AcademicClient;
import com.sugamflow.school.exam.integration.StudentAccessClient;
import com.sugamflow.school.exam.integration.StudentDirectoryClient;
import com.sugamflow.school.exam.persistence.entity.ExamDefinitionEntity;
import com.sugamflow.school.exam.persistence.entity.ExamMarkAuditEntity;
import com.sugamflow.school.exam.persistence.entity.ExamMarkEntity;
import com.sugamflow.school.exam.persistence.repo.ExamDefinitionRepository;
import com.sugamflow.school.exam.persistence.repo.ExamMarkRepository;
import com.sugamflow.school.exam.web.ExamException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradebookServiceTest {

  @Mock private ExamDefinitionRepository definitions;
  @Mock private ExamMarkRepository marks;
  @Mock private com.sugamflow.school.exam.persistence.repo.ExamMarkAuditRepository audits;
  @Mock private com.sugamflow.school.exam.persistence.repo.ExamEntryPolicyRepository policies;
  @Mock private AcademicClient academic;
  @Mock private StudentDirectoryClient directory;
  @Mock private StudentAccessClient studentAccess;
  @Mock private GradingSupport grading;
  @InjectMocks private GradebookService service;

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void createDefinitionRequiresFields() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    ExamException ex =
        assertThrows(ExamException.class, () -> service.createDefinition(Map.of("name", "UT1")));
    assertEquals("VALIDATION", ex.getCode());
  }

  @Test
  void gradebookJoinsRosterAndMarks() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID examId = UUID.randomUUID();
    UUID sectionId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    ExamDefinitionEntity exam = new ExamDefinitionEntity();
    exam.setId(examId);
    exam.setOrganizationId("demo-school");
    exam.setSectionId(sectionId);
    exam.setSubjectId(subjectId);
    exam.setName("UT1");
    exam.setTermKey("TERM1");
    exam.setMaxMarks(new BigDecimal("50"));
    exam.setStatus("OPEN");
    when(definitions.findByIdAndOrganizationId(examId, "demo-school"))
        .thenReturn(Optional.of(exam));
    when(academic.getSection(any(), eq(sectionId.toString())))
        .thenReturn(Map.of("studentLabel", "Grade 8-A"));

    ExamMarkEntity mark = new ExamMarkEntity();
    mark.setStudentId(studentId);
    mark.setMarksObtained(new BigDecimal("42"));
    mark.setGrade("A");
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(examId)).thenReturn(List.of(mark));
    when(directory.listByClassSection(any(), eq("Grade 8-A"), anyInt()))
        .thenReturn(
            List.of(
                Map.of(
                    "id", studentId.toString(),
                    "admissionNo", "ADM-1",
                    "fullName", "Asha")));

    Map<String, Object> out = service.gradebook(examId);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> students = (List<Map<String, Object>>) out.get("students");
    assertEquals(1, students.size());
    assertEquals(new BigDecimal("42"), students.get(0).get("marksObtained"));
    assertEquals("A", students.get(0).get("grade"));
  }

  @Test
  void bulkSaveRejectsLockedExam() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID examId = UUID.randomUUID();
    ExamDefinitionEntity exam = new ExamDefinitionEntity();
    exam.setId(examId);
    exam.setOrganizationId("demo-school");
    exam.setSectionId(UUID.randomUUID());
    exam.setSubjectId(UUID.randomUUID());
    exam.setMaxMarks(new BigDecimal("100"));
    exam.setStatus("LOCKED");
    when(definitions.findByIdAndOrganizationId(examId, "demo-school"))
        .thenReturn(Optional.of(exam));

    ExamException ex =
        assertThrows(
            ExamException.class,
            () ->
                service.bulkSave(
                    Map.of(
                        "examDefinitionId",
                        examId.toString(),
                        "marks",
                        List.of(Map.of("admissionNo", "ADM-1", "marksObtained", 10)))));
    assertEquals("LOCKED", ex.getCode());
  }

  @Test
  void clearMarkRejectsCorrectionRequest() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID examId = UUID.randomUUID();
    ExamDefinitionEntity exam = new ExamDefinitionEntity();
    exam.setId(examId);
    exam.setOrganizationId("demo-school");
    exam.setSectionId(UUID.randomUUID());
    exam.setSubjectId(UUID.randomUUID());
    exam.setMaxMarks(new BigDecimal("25"));
    exam.setStatus("CORRECTION_REQUESTED");
    when(definitions.findByIdAndOrganizationId(examId, "demo-school")).thenReturn(Optional.of(exam));

    ExamException ex =
        assertThrows(
            ExamException.class,
            () ->
                service.clearMark(
                    Map.of(
                        "examDefinitionId",
                        examId.toString(),
                        "admissionNo",
                        "ADM-1",
                        "reason",
                        "Entered on the wrong student")));
    assertEquals("SUBMITTED", ex.getCode());
  }

  @Test
  void clearMarkDeletesOnlyTheMarksRow() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID examId = UUID.randomUUID();
    UUID sectionId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    ExamDefinitionEntity exam = new ExamDefinitionEntity();
    exam.setId(examId);
    exam.setOrganizationId("demo-school");
    exam.setSectionId(sectionId);
    exam.setSubjectId(subjectId);
    exam.setName("UT1");
    exam.setTermKey("TERM1");
    exam.setMaxMarks(new BigDecimal("25"));
    exam.setStatus("OPEN");
    when(definitions.findByIdAndOrganizationId(examId, "demo-school")).thenReturn(Optional.of(exam));
    ExamMarkEntity mark = new ExamMarkEntity();
    mark.setId(UUID.randomUUID());
    mark.setStudentId(studentId);
    mark.setAdmissionNo("ADM-1");
    mark.setStudentName("Asha");
    mark.setMarksObtained(new BigDecimal("20"));
    mark.setEntryStatus("PRESENT");
    when(marks.findByExamDefinitionIdAndStudentId(examId, studentId)).thenReturn(Optional.of(mark));
    when(academic.getSection(any(), eq(sectionId.toString())))
        .thenReturn(Map.of("studentLabel", "Grade 8-A"));
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(examId)).thenReturn(List.of());
    when(directory.listByClassSection(any(), eq("Grade 8-A"), anyInt()))
        .thenReturn(List.of(Map.of("id", studentId.toString(), "admissionNo", "ADM-1", "fullName", "Asha")));

    Map<String, Object> out =
        service.clearMark(
            Map.of(
                "examDefinitionId",
                examId.toString(),
                "studentId",
                studentId.toString(),
                "reason",
                "Wrong student"));

    verify(marks).delete(mark);
    verify(definitions).save(exam);
    assertEquals("OPEN", ((Map<?, ?>) out.get("exam")).get("status"));
  }

  @Test
  void applyCorrectionOpensTheExamAndKeepsTheNote() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "PRINCIPAL"));
    ExamDefinitionEntity exam = correctionExam("SUBMITTED");
    when(definitions.findByIdAndOrganizationId(exam.getId(), "demo-school")).thenReturn(Optional.of(exam));

    Map<String, Object> out = service.applyCorrection(exam.getId(), Map.of("reason", "Total was wrong"));

    assertEquals("OPEN", out.get("status"));
    assertNull(exam.getStatusBeforeCorrection());
    ExamMarkAuditEntity audit = savedAudit();
    assertEquals("CORRECTION_REQUESTED", audit.getPreviousStatus());
    assertEquals("OPEN", audit.getNewStatus());
    assertEquals("Total was wrong", audit.getReason());
    assertEquals("admin", audit.getChangedBy());
  }

  @Test
  void rejectCorrectionRestoresThePreviousReadOnlyStatus() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    ExamDefinitionEntity exam = correctionExam("PUBLISHED");
    when(definitions.findByIdAndOrganizationId(exam.getId(), "demo-school")).thenReturn(Optional.of(exam));

    Map<String, Object> out = service.rejectCorrection(exam.getId(), Map.of("reason", "Marks are already correct"));

    assertEquals("PUBLISHED", out.get("status"));
    assertEquals("PUBLISHED", exam.getStatus());
    assertNull(exam.getStatusBeforeCorrection());
    ExamMarkAuditEntity audit = savedAudit();
    assertEquals("CORRECTION_REQUESTED", audit.getPreviousStatus());
    assertEquals("PUBLISHED", audit.getNewStatus());
    assertEquals("Marks are already correct", audit.getReason());
  }

  @Test
  void rejectCorrectionUsesPublishedAtWhenPriorStatusWasNotStored() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "ADMIN"));
    ExamDefinitionEntity exam = correctionExam(null);
    exam.setPublishedAt(java.time.Instant.parse("2026-09-01T00:00:00Z"));
    when(definitions.findByIdAndOrganizationId(exam.getId(), "demo-school")).thenReturn(Optional.of(exam));

    service.rejectCorrection(exam.getId(), Map.of("reason", "Keep the published result"));

    assertEquals("PUBLISHED", exam.getStatus());
  }

  @Test
  void applyCorrectionRequiresANoteAndBlocksSubjectTeachers() {
    ExamDefinitionEntity exam = correctionExam("SUBMITTED");
    when(definitions.findByIdAndOrganizationId(exam.getId(), "demo-school")).thenReturn(Optional.of(exam));
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "PRINCIPAL"));
    ExamException missing =
        assertThrows(ExamException.class, () -> service.applyCorrection(exam.getId(), Map.of()));
    assertEquals("VALIDATION", missing.getCode());

    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "teacher1", "TEACHER"));
    when(academic.teacherScope(any()))
        .thenReturn(
            Map.of(
                "assignments",
                List.of(
                    Map.of(
                        "sectionId", exam.getSectionId().toString(),
                        "subjectId", exam.getSubjectId().toString()))));
    assertThrows(SecurityException.class, () -> service.applyCorrection(exam.getId(), Map.of("reason", "Please")));
    assertEquals("CORRECTION_REQUESTED", exam.getStatus());
  }

  @Test
  void classTeacherCanApplyCorrection() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "teacher1", "TEACHER"));
    ExamDefinitionEntity exam = correctionExam("SUBMITTED");
    when(definitions.findByIdAndOrganizationId(exam.getId(), "demo-school")).thenReturn(Optional.of(exam));
    when(academic.teacherScope(any()))
        .thenReturn(Map.of("classTeacherSectionIds", List.of(exam.getSectionId().toString())));

    Map<String, Object> out = service.applyCorrection(exam.getId(), Map.of("reason", "Class teacher approved"));

    assertEquals("OPEN", out.get("status"));
  }

  @Test
  void unlockDoesNotBypassACorrectionReview() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    ExamDefinitionEntity exam = correctionExam("SUBMITTED");
    when(definitions.findByIdAndOrganizationId(exam.getId(), "demo-school")).thenReturn(Optional.of(exam));

    ExamException ex =
        assertThrows(ExamException.class, () -> service.unlock(exam.getId(), Map.of("reason", "skip")));
    assertEquals("VALIDATION", ex.getCode());
    assertEquals("CORRECTION_REQUESTED", exam.getStatus());
  }

  @Test
  void sectionGridShowsOneColumnPerExamAndHidesOtherSubjectsFromATeacher() {
    UUID sectionId = UUID.randomUUID();
    UUID mathId = UUID.randomUUID();
    UUID englishId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    ExamDefinitionEntity math = gridExam(sectionId, mathId, "Unit Test", "OPEN");
    ExamDefinitionEntity english = gridExam(sectionId, englishId, "Unit Test", "SUBMITTED");
    when(definitions.findByOrganizationIdAndSectionIdOrderByUpdatedAtDesc(eq("demo-school"), eq(sectionId)))
        .thenReturn(List.of(math, english));
    when(academic.getSection(any(), eq(sectionId.toString())))
        .thenReturn(Map.of("studentLabel", "Grade 8-A", "classId", "class-8"));
    when(academic.listSubjects(any()))
        .thenReturn(
            List.of(
                Map.of("id", mathId.toString(), "name", "Mathematics"),
                Map.of("id", englishId.toString(), "name", "English")));
    when(academic.listClasses(any())).thenReturn(List.of(Map.of("id", "class-8", "name", "Grade 8")));
    ExamMarkEntity mark = new ExamMarkEntity();
    mark.setStudentId(studentId);
    mark.setMarksObtained(new BigDecimal("18"));
    mark.setEntryStatus("PRESENT");
    mark.setGrade("A");
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(math.getId())).thenReturn(List.of(mark));
    when(marks.findByExamDefinitionIdOrderByStudentNameAsc(english.getId())).thenReturn(List.of());
    when(directory.listByClassSection(any(), eq("Grade 8-A"), anyInt()))
        .thenReturn(
            List.of(
                Map.of(
                    "id", studentId.toString(),
                    "admissionNo", "ADM-1",
                    "fullName", "Asha",
                    "rollNo", "4")));

    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    Map<String, Object> out = service.sectionGrid(sectionId);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> columns = (List<Map<String, Object>>) out.get("columns");
    assertEquals(2, columns.size());
    assertEquals("English", columns.get(0).get("subjectName"));
    assertEquals(Boolean.TRUE, columns.get(0).get("readOnly"));
    assertEquals("Mathematics", columns.get(1).get("subjectName"));
    assertEquals(Boolean.FALSE, columns.get(1).get("readOnly"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> students = (List<Map<String, Object>>) out.get("students");
    @SuppressWarnings("unchecked")
    Map<String, Object> cells = (Map<String, Object>) students.get(0).get("cells");
    @SuppressWarnings("unchecked")
    Map<String, Object> mathCell = (Map<String, Object>) cells.get(math.getId().toString());
    assertEquals(new BigDecimal("18"), mathCell.get("marks"));
    assertEquals("Grade 8", out.get("className"));

    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "teacher1", "TEACHER"));
    when(academic.teacherScope(any()))
        .thenReturn(
            Map.of(
                "assignments",
                List.of(Map.of("sectionId", sectionId.toString(), "subjectId", englishId.toString()))));
    Map<String, Object> scoped = service.sectionGrid(sectionId);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> visible = (List<Map<String, Object>>) scoped.get("columns");
    assertEquals(1, visible.size());
    assertEquals("English", visible.get(0).get("subjectName"));

    math.setAcademicSessionId("2025-26");
    english.setAcademicSessionId("2025-26");
    TenantContext.set(new TenantScope("demo-school", "main", null, "admin", "SHOP_OWNER"));
    assertEquals("2025-26", service.sectionGrid(sectionId).get("academicSessionId"));
  }

  private ExamDefinitionEntity gridExam(UUID sectionId, UUID subjectId, String name, String status) {
    ExamDefinitionEntity exam = new ExamDefinitionEntity();
    exam.setId(UUID.randomUUID());
    exam.setOrganizationId("demo-school");
    exam.setSectionId(sectionId);
    exam.setSubjectId(subjectId);
    exam.setName(name);
    exam.setTermKey("TERM1");
    exam.setMaxMarks(new BigDecimal("25"));
    exam.setStatus(status);
    return exam;
  }

  private ExamDefinitionEntity correctionExam(String prior) {
    ExamDefinitionEntity exam = new ExamDefinitionEntity();
    exam.setId(UUID.randomUUID());
    exam.setOrganizationId("demo-school");
    exam.setSectionId(UUID.randomUUID());
    exam.setSubjectId(UUID.randomUUID());
    exam.setName("Unit Test");
    exam.setTermKey("TERM1");
    exam.setMaxMarks(new BigDecimal("25"));
    exam.setStatus("CORRECTION_REQUESTED");
    exam.setStatusBeforeCorrection(prior);
    return exam;
  }

  private ExamMarkAuditEntity savedAudit() {
    ArgumentCaptor<ExamMarkAuditEntity> captor = ArgumentCaptor.forClass(ExamMarkAuditEntity.class);
    verify(audits).save(captor.capture());
    return captor.getValue();
  }
}
