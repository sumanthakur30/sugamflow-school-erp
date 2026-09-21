package com.sugamflow.school.exam.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.integration.AcademicClient;
import com.sugamflow.school.exam.persistence.entity.ExamDefinitionEntity;
import com.sugamflow.school.exam.persistence.entity.ExamMarkEntity;
import com.sugamflow.school.exam.persistence.repo.ExamDefinitionRepository;
import com.sugamflow.school.exam.persistence.repo.ExamMarkRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explainable performance insights derived from published marks. This intentionally avoids opaque
 * model output: every strength, focus area, and trend includes the evidence used to produce it.
 */
@Service
public class ReportCardInsightService {

  private final ReportCardService reportCards;
  private final ExamDefinitionRepository definitions;
  private final ExamMarkRepository marks;
  private final AcademicClient academic;

  public ReportCardInsightService(
      ReportCardService reportCards,
      ExamDefinitionRepository definitions,
      ExamMarkRepository marks,
      AcademicClient academic) {
    this.reportCards = reportCards;
    this.definitions = definitions;
    this.marks = marks;
    this.academic = academic;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> studentInsights(
      UUID sectionId, String termKey, String studentKey, boolean publishedOnly) {
    Map<String, Object> card =
        reportCards.studentTermReport(sectionId, termKey, studentKey, publishedOnly);
    return analyze(card, sectionId, studentKey);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> myStudentInsights(
      UUID sectionId, String termKey, String studentKey) {
    Map<String, Object> card = reportCards.myStudentTermReport(sectionId, termKey, studentKey);
    return analyze(card, sectionId, studentKey);
  }

  private Map<String, Object> analyze(
      Map<String, Object> card, UUID sectionId, String studentKey) {
    TenantScope scope = TenantContext.require();
    @SuppressWarnings("unchecked")
    Map<String, Object> student = (Map<String, Object>) card.get("student");
    String studentId = text(student.get("studentId"));
    String admissionNo = text(student.get("admissionNo"));
    String currentTerm = text(card.get("termKey"));

    List<ExamMarkEntity> studentMarks = new ArrayList<>();
    if (studentId != null && uuid(studentId) != null) {
      studentMarks.addAll(
          marks.findByOrganizationIdAndStudentIdOrderByUpdatedAtDesc(
              scope.organizationId(), uuid(studentId)));
    }
    if (admissionNo != null || studentMarks.isEmpty()) {
      studentMarks.addAll(
          marks.findByOrganizationIdAndAdmissionNoOrderByUpdatedAtDesc(
              scope.organizationId(), admissionNo == null ? studentKey : admissionNo));
    }
    Map<UUID, ExamMarkEntity> markByDefinition = new HashMap<>();
    for (ExamMarkEntity mark : studentMarks) {
      markByDefinition.put(mark.getExamDefinitionId(), mark);
    }

    Map<String, String> subjectNames = new HashMap<>();
    for (Map<String, Object> subject : academic.listSubjects(scope)) {
      subjectNames.put(
          text(subject.get("id")),
          firstNonBlank(text(subject.get("name")), text(subject.get("code")), "Subject"));
    }

    List<ExamDefinitionEntity> published =
        definitions.findByOrganizationIdAndSectionIdOrderByUpdatedAtDesc(
                scope.organizationId(), sectionId)
            .stream()
            .filter(definition -> "PUBLISHED".equalsIgnoreCase(definition.getStatus()))
            .toList();
    Map<String, List<ExamDefinitionEntity>> byTerm = new LinkedHashMap<>();
    Map<String, Instant> termDate = new HashMap<>();
    for (ExamDefinitionEntity definition : published) {
      byTerm.computeIfAbsent(definition.getTermKey(), ignored -> new ArrayList<>()).add(definition);
      termDate.merge(
          definition.getTermKey(),
          definition.getUpdatedAt(),
          (left, right) -> left.isAfter(right) ? left : right);
    }

    List<TermSnapshot> snapshots = new ArrayList<>();
    for (Map.Entry<String, List<ExamDefinitionEntity>> entry : byTerm.entrySet()) {
      BigDecimal obtained = BigDecimal.ZERO;
      BigDecimal maximum = BigDecimal.ZERO;
      int assessed = 0;
      for (ExamDefinitionEntity definition : entry.getValue()) {
        ExamMarkEntity mark = markByDefinition.get(definition.getId());
        if (mark == null || mark.getMarksObtained() == null || definition.getMaxMarks() == null) {
          continue;
        }
        obtained = obtained.add(mark.getMarksObtained());
        maximum = maximum.add(definition.getMaxMarks());
        assessed++;
      }
      if (assessed > 0 && maximum.compareTo(BigDecimal.ZERO) > 0) {
        snapshots.add(
            new TermSnapshot(
                entry.getKey(),
                percentage(obtained, maximum),
                assessed,
                termDate.getOrDefault(entry.getKey(), Instant.EPOCH)));
      }
    }
    snapshots.sort(Comparator.comparing(TermSnapshot::date));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> currentSubjects =
        (List<Map<String, Object>>) student.getOrDefault("subjects", List.of());
    List<SubjectPerformance> performance = new ArrayList<>();
    for (Map<String, Object> subject : currentSubjects) {
      BigDecimal obtained = decimal(subject.get("marksObtained"));
      BigDecimal maximum = decimal(subject.get("maxMarks"));
      if (obtained == null || maximum == null || maximum.compareTo(BigDecimal.ZERO) <= 0) continue;
      String subjectId = text(subject.get("subjectId"));
      performance.add(
          new SubjectPerformance(
              subjectId,
              firstNonBlank(
                  text(subject.get("name")),
                  subjectNames.get(subjectId),
                  "Subject"),
              percentage(obtained, maximum)));
    }
    performance.sort(Comparator.comparing(SubjectPerformance::percentage).reversed());

    BigDecimal currentPercentage =
        decimal(student.get("percentage")) == null
            ? BigDecimal.ZERO
            : decimal(student.get("percentage"));
    TermSnapshot currentSnapshot =
        snapshots.stream()
            .filter(snapshot -> snapshot.term().equalsIgnoreCase(currentTerm))
            .findFirst()
            .orElse(null);
    TermSnapshot previous =
        previousSnapshot(snapshots, currentSnapshot, currentTerm);
    BigDecimal change =
        previous == null ? null : currentPercentage.subtract(previous.percentage());
    String trend = trend(change);

    List<Map<String, Object>> strengths = new ArrayList<>();
    for (SubjectPerformance subject : performance) {
      if (strengths.size() >= 3) break;
      if (subject.percentage().compareTo(new BigDecimal("75")) >= 0
          || subject.percentage().compareTo(currentPercentage.add(new BigDecimal("5"))) >= 0) {
        strengths.add(
            insightItem(
                subject,
                "Strong understanding",
                "Continue extension work and apply this strength in projects or peer learning."));
      }
    }
    if (strengths.isEmpty() && !performance.isEmpty()) {
      SubjectPerformance best = performance.get(0);
      strengths.add(
          insightItem(
              best,
              "Best-performing subject",
              "Keep practising consistently to convert this into a clear strength."));
    }

    List<Map<String, Object>> focusAreas = new ArrayList<>();
    List<SubjectPerformance> weakest = new ArrayList<>(performance);
    weakest.sort(Comparator.comparing(SubjectPerformance::percentage));
    for (SubjectPerformance subject : weakest) {
      if (focusAreas.size() >= 3) break;
      if (subject.percentage().compareTo(new BigDecimal("60")) < 0
          || subject.percentage().compareTo(currentPercentage.subtract(new BigDecimal("8"))) <= 0) {
        focusAreas.add(
            insightItem(
                subject,
                focusLabel(subject.percentage()),
                recommendation(subject.name(), subject.percentage())));
      }
    }

    List<String> actions = new ArrayList<>();
    if (!focusAreas.isEmpty()) {
      actions.add(
          "Prioritize "
              + focusAreas.stream()
                  .limit(2)
                  .map(item -> String.valueOf(item.get("subject")))
                  .reduce((a, b) -> a + " and " + b)
                  .orElse("the focus subjects")
              + " in the next study plan.");
      actions.add("Use one short diagnostic exercise each week and review incorrect answers.");
    } else {
      actions.add("Maintain the current study routine and add one higher-order task per subject.");
    }
    if ("DECLINING".equals(trend)) {
      actions.add("Review the term-to-term decline with the teacher and agree on a two-week intervention.");
    } else if ("IMPROVING".equals(trend)) {
      actions.add("Continue the routines that produced the improvement and set a realistic next-term target.");
    }

    List<Map<String, Object>> termTrend =
        snapshots.stream()
            .map(
                snapshot -> {
                  Map<String, Object> row = new LinkedHashMap<>();
                  row.put("termKey", snapshot.term());
                  row.put("percentage", snapshot.percentage());
                  row.put("assessments", snapshot.assessments());
                  row.put("current", snapshot.term().equalsIgnoreCase(currentTerm));
                  return row;
                })
            .toList();

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("currentPercentage", currentPercentage);
    summary.put("previousPercentage", previous == null ? null : previous.percentage());
    summary.put("changePercentagePoints", change);
    summary.put("trend", trend);
    summary.put("overallMessage", overallMessage(trend, currentPercentage, change));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("studentId", studentId);
    result.put("admissionNo", admissionNo);
    result.put("studentName", student.get("studentName"));
    result.put("sectionId", sectionId.toString());
    result.put("termKey", currentTerm);
    result.put("summary", summary);
    result.put("strengths", strengths);
    result.put("focusAreas", focusAreas);
    result.put("recommendedActions", actions);
    result.put("termTrend", termTrend);
    result.put("evidence", Map.of("subjectsAnalyzed", performance.size(), "termsAnalyzed", snapshots.size()));
    result.put(
        "disclaimer",
        "Insights are generated from published marks only. Teachers should apply professional judgment and consider attendance, learning needs, and context.");
    return result;
  }

  private static TermSnapshot previousSnapshot(
      List<TermSnapshot> snapshots, TermSnapshot current, String currentTerm) {
    if (snapshots.size() < 2) return null;
    int currentIndex = current == null ? -1 : snapshots.indexOf(current);
    if (currentIndex > 0) return snapshots.get(currentIndex - 1);
    for (int i = snapshots.size() - 1; i >= 0; i--) {
      if (!snapshots.get(i).term().equalsIgnoreCase(currentTerm)) return snapshots.get(i);
    }
    return null;
  }

  private static Map<String, Object> insightItem(
      SubjectPerformance subject, String label, String recommendation) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("subjectId", subject.id());
    item.put("subject", subject.name());
    item.put("percentage", subject.percentage());
    item.put("label", label);
    item.put("recommendation", recommendation);
    return item;
  }

  private static String trend(BigDecimal change) {
    if (change == null) return "NO_HISTORY";
    if (change.compareTo(new BigDecimal("3")) >= 0) return "IMPROVING";
    if (change.compareTo(new BigDecimal("-3")) <= 0) return "DECLINING";
    return "STABLE";
  }

  private static String overallMessage(
      String trend, BigDecimal current, BigDecimal change) {
    if ("IMPROVING".equals(trend)) {
      return "Performance improved by "
          + change.setScale(1, RoundingMode.HALF_UP)
          + " percentage points from the previous assessed term.";
    }
    if ("DECLINING".equals(trend)) {
      return "Performance declined by "
          + change.abs().setScale(1, RoundingMode.HALF_UP)
          + " percentage points; targeted support is recommended.";
    }
    if ("STABLE".equals(trend)) {
      return "Performance is stable across the two most recent assessed terms.";
    }
    return "Current performance is "
        + current.setScale(1, RoundingMode.HALF_UP)
        + "%. More published terms are needed for a trend.";
  }

  private static String focusLabel(BigDecimal percentage) {
    if (percentage.compareTo(new BigDecimal("33")) < 0) return "Immediate support needed";
    if (percentage.compareTo(new BigDecimal("50")) < 0) return "Foundation needs reinforcement";
    return "Opportunity to improve";
  }

  private static String recommendation(String subject, BigDecimal percentage) {
    if (percentage.compareTo(new BigDecimal("33")) < 0) {
      return "Revisit prerequisite concepts in "
          + subject
          + " with teacher-guided practice before new material.";
    }
    if (percentage.compareTo(new BigDecimal("50")) < 0) {
      return "Schedule two focused practice sessions weekly and correct errors with feedback.";
    }
    return "Practise mixed questions weekly and explain solutions aloud to strengthen recall.";
  }

  private static BigDecimal percentage(BigDecimal obtained, BigDecimal maximum) {
    return obtained
        .multiply(new BigDecimal("100"))
        .divide(maximum, 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal decimal(Object value) {
    if (value == null) return null;
    try {
      return value instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static UUID uuid(String value) {
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static String text(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private record SubjectPerformance(String id, String name, BigDecimal percentage) {}

  private record TermSnapshot(
      String term, BigDecimal percentage, int assessments, Instant date) {}
}
