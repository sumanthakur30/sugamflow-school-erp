package com.sugamflow.school.attendance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LowAttendanceFiguresTest {

  @Test
  void theAskQueryKeepsTheAttendancePercentAndDropsOtherFigures() {
    List<Map<String, Object>> rows =
        LowAttendanceFigures.fromDesk(
            List.of(
                Map.of(
                    "studentName", "Rohan",
                    "admissionNo", "ADM-2",
                    "status", "ABSENT",
                    "percent", 60,
                    "classRank", 1,
                    "mobile", "9999999999")));

    assertEquals(1, rows.size());
    assertEquals(60, rows.get(0).get("percent"));
    assertEquals("ABSENT", rows.get(0).get("status"));
    assertFalse(rows.get(0).containsKey("classRank"));
    assertFalse(rows.get(0).containsKey("mobile"));
  }
}
