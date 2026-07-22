package com.sugamflow.school.exam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradebookServiceTest {

  @Mock private ExamDefinitionRepository definitions;
  @Mock private ExamMarkRepository marks;
  @Mock private AcademicClient academic;
  @Mock private StudentDirectoryClient directory;
  @Mock private StudentAccessClient studentAccess;
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
}
