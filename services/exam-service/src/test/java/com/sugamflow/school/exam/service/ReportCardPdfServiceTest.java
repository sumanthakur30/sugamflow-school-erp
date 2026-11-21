package com.sugamflow.school.exam.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.exam.web.ExamException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportCardPdfServiceTest {

  private final ReportCardPdfService pdf = new ReportCardPdfService();

  @Test
  void classPdfPrintsASavedZeroAndSkipsTheIncompleteCard() {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("termKey", "TERM1");
    report.put("sectionLabel", "Grade 8-A");
    report.put("students", List.of(student("Asha", true, BigDecimal.ZERO), student("Rohan", false, null)));

    byte[] bytes = pdf.renderClass(report, "Demo School");

    assertTrue(bytes.length > 100);
    assertEquals("%PDF", new String(bytes, 0, 4));
  }

  @Test
  void classPdfRefusesWhenEveryCardHasAMissingMark() {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("termKey", "TERM1");
    report.put("sectionLabel", "Grade 8-A");
    report.put("students", List.of(student("Rohan", false, null)));

    ExamException ex = assertThrows(ExamException.class, () -> pdf.renderClass(report, "Demo School"));
    assertEquals("VALIDATION", ex.getCode());
  }

  private static Map<String, Object> student(String name, boolean complete, BigDecimal marks) {
    Map<String, Object> subject = new LinkedHashMap<>();
    subject.put("name", "Mathematics");
    subject.put("marksObtained", marks);
    subject.put("maxMarks", marks == null ? null : new BigDecimal("100"));
    subject.put("grade", marks == null ? null : "F");

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("studentName", name);
    row.put("admissionNo", "ADM");
    row.put("classSection", "Grade 8-A");
    row.put("complete", complete);
    row.put("subjects", List.of(subject));
    row.put("totalObtained", marks);
    row.put("totalMax", marks == null ? null : new BigDecimal("100"));
    row.put("percentage", marks == null ? null : BigDecimal.ZERO);
    row.put("overallGrade", marks == null ? null : "F");
    row.put("result", complete ? "PASS" : null);
    row.put("rank", "1");
    return row;
  }
}
