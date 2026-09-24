package com.sugamflow.school.exam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.integration.AcademicClient;
import com.sugamflow.school.exam.persistence.entity.ExamDefinitionEntity;
import com.sugamflow.school.exam.persistence.entity.ExamMarkEntity;
import com.sugamflow.school.exam.persistence.repo.ExamDefinitionRepository;
import com.sugamflow.school.exam.persistence.repo.ExamMarkRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportCardInsightServiceTest {

  @Mock private ReportCardService reportCards;
  @Mock private ExamDefinitionRepository definitions;
  @Mock private ExamMarkRepository marks;
  @Mock private AcademicClient academic;
  @InjectMocks private ReportCardInsightService service;

  private final UUID sectionId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID mathId = UUID.randomUUID();
  private final UUID scienceId = UUID.randomUUID();

  @BeforeEach
  void setTenant() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "ADMIN"));
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void producesEvidenceBasedTrendStrengthsAndFocusAreas() {
    ExamDefinitionEntity term1Math = definition("TERM1", mathId, "2026-01-01T00:00:00Z");
    ExamDefinitionEntity term1Science = definition("TERM1", scienceId, "2026-01-01T00:00:00Z");
    ExamDefinitionEntity term2Math = definition("TERM2", mathId, "2026-04-01T00:00:00Z");
    ExamDefinitionEntity term2Science = definition("TERM2", scienceId, "2026-04-01T00:00:00Z");

    when(reportCards.studentTermReport(sectionId, "TERM2", studentId.toString(), true))
        .thenReturn(currentCard());
    when(definitions.findByOrganizationIdAndSectionIdOrderByUpdatedAtDesc(
            "demo-school", sectionId))
        .thenReturn(List.of(term2Math, term2Science, term1Math, term1Science));
    when(marks.findByOrganizationIdAndStudentIdOrderByUpdatedAtDesc(
            "demo-school", studentId))
        .thenReturn(
            List.of(
                mark(term1Math, "60"),
                mark(term1Science, "40"),
                mark(term2Math, "90"),
                mark(term2Science, "45")));
    when(academic.listSubjects(any()))
        .thenReturn(
            List.of(
                Map.of("id", mathId.toString(), "name", "Mathematics"),
                Map.of("id", scienceId.toString(), "name", "Science")));

    Map<String, Object> insight =
        service.studentInsights(sectionId, "TERM2", studentId.toString(), true);

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) insight.get("summary");
    assertEquals("IMPROVING", summary.get("trend"));
    assertEquals(new BigDecimal("17.50"), summary.get("changePercentagePoints"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> strengths = (List<Map<String, Object>>) insight.get("strengths");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> focus = (List<Map<String, Object>>) insight.get("focusAreas");
    assertEquals("Mathematics", strengths.get(0).get("subject"));
    assertEquals("Science", focus.get(0).get("subject"));
    assertFalse(((List<?>) insight.get("recommendedActions")).isEmpty());
    assertEquals(2, ((List<?>) insight.get("termTrend")).size());
  }

  @Test
  void reportsNoHistoryWithoutInventingATrend() {
    ExamDefinitionEntity term2Math = definition("TERM2", mathId, "2026-04-01T00:00:00Z");
    when(reportCards.studentTermReport(sectionId, "TERM2", studentId.toString(), true))
        .thenReturn(currentCard());
    when(definitions.findByOrganizationIdAndSectionIdOrderByUpdatedAtDesc(
            "demo-school", sectionId))
        .thenReturn(List.of(term2Math));
    when(marks.findByOrganizationIdAndStudentIdOrderByUpdatedAtDesc(
            "demo-school", studentId))
        .thenReturn(List.of(mark(term2Math, "90")));
    when(academic.listSubjects(any())).thenReturn(List.of());

    Map<String, Object> insight =
        service.studentInsights(sectionId, "TERM2", studentId.toString(), true);
    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) insight.get("summary");
    assertEquals("NO_HISTORY", summary.get("trend"));
    assertTrue(String.valueOf(summary.get("overallMessage")).contains("More published terms"));
  }

  private Map<String, Object> currentCard() {
    return Map.of(
        "sectionId",
        sectionId.toString(),
        "termKey",
        "TERM2",
        "student",
        Map.of(
            "studentId",
            studentId.toString(),
            "admissionNo",
            "ADM-1",
            "studentName",
            "Asha",
            "percentage",
            new BigDecimal("67.50"),
            "subjects",
            List.of(
                Map.of(
                    "subjectId",
                    mathId.toString(),
                    "name",
                    "Mathematics",
                    "marksObtained",
                    new BigDecimal("90"),
                    "maxMarks",
                    new BigDecimal("100")),
                Map.of(
                    "subjectId",
                    scienceId.toString(),
                    "name",
                    "Science",
                    "marksObtained",
                    new BigDecimal("45"),
                    "maxMarks",
                    new BigDecimal("100")))));
  }

  private ExamDefinitionEntity definition(String term, UUID subject, String updatedAt) {
    ExamDefinitionEntity definition = new ExamDefinitionEntity();
    definition.setId(UUID.randomUUID());
    definition.setOrganizationId("demo-school");
    definition.setSectionId(sectionId);
    definition.setSubjectId(subject);
    definition.setTermKey(term);
    definition.setName(term + " exam");
    definition.setMaxMarks(new BigDecimal("100"));
    definition.setStatus("PUBLISHED");
    definition.setUpdatedAt(Instant.parse(updatedAt));
    return definition;
  }

  private ExamMarkEntity mark(ExamDefinitionEntity definition, String obtained) {
    ExamMarkEntity mark = new ExamMarkEntity();
    mark.setId(UUID.randomUUID());
    mark.setOrganizationId("demo-school");
    mark.setExamDefinitionId(definition.getId());
    mark.setStudentId(studentId);
    mark.setMarksObtained(new BigDecimal(obtained));
    return mark;
  }
}
