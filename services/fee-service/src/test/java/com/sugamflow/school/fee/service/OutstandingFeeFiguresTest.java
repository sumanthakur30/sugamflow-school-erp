package com.sugamflow.school.fee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutstandingFeeFiguresTest {

  @Test
  void theAskQueryKeepsTheFeeDeskAmountAndDropsOtherFigures() {
    List<Map<String, Object>> rows =
        OutstandingFeeFigures.fromDesk(
            List.of(
                Map.of(
                    "studentName", "Asha",
                    "admissionNo", "ADM-1",
                    "classSection", "7-A",
                    "feeHead", "TUITION",
                    "dueDate", "2026-04-01",
                    "amount", 5000,
                    "mobile", "9999999999",
                    "pendingFees", 99999)));

    assertEquals(1, rows.size());
    assertEquals(5000, rows.get(0).get("amount"));
    assertEquals("ADM-1", rows.get(0).get("admissionNo"));
    assertFalse(rows.get(0).containsKey("mobile"));
    assertFalse(rows.get(0).containsKey("pendingFees"));
  }
}
