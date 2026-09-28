package com.sugamflow.school.student.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UdiseColumnsTest {

  @Test
  void keepsKnownColumnsInCatalogOrder() {
    List<String> ordered =
        UdiseColumns.keepKnownInOrder(List.of("samagraId", "not-a-column", "fullName", "penNumber"));
    assertEquals(List.of("fullName", "penNumber", "samagraId"), ordered);
  }

  @Test
  void mapsStoredAnswersAndLeavesMissingFieldsBlank() {
    Map<String, Object> answers =
        Map.of(
            "studentName", "Asha",
            "classSection", "VIII-A",
            "rte", "yes",
            "category", "OBC");
    assertEquals("Asha", UdiseColumns.cell("ADM-1", answers, "fullName"));
    assertEquals("VIII", UdiseColumns.cell("ADM-1", answers, "className"));
    assertEquals("A", UdiseColumns.cell("ADM-1", answers, "section"));
    assertEquals("ADM-1", UdiseColumns.cell("ADM-1", answers, "admissionNo"));
    assertEquals("Yes", UdiseColumns.cell("ADM-1", answers, "rte"));
    assertEquals("", UdiseColumns.cell("ADM-1", answers, "bloodGroup"));
  }

  @Test
  void quotesCommasAndGuardsFormulas() {
    assertEquals("\"Kumar, Asha\"", UdiseColumns.csv(UdiseColumns.cell("1", Map.of("fullName", "Kumar, Asha"), "fullName")));
    assertTrue(UdiseColumns.cell("1", Map.of("fullName", "=cmd"), "fullName").startsWith("'"));
  }
}
