package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InstallmentScheduleTest {

  @Test
  void onePaidPortionLeavesTheYearOpenAndAPaidAmountStaysPut() {
    List<Map<String, Object>> portions =
        List.of(
            Map.of("key", "apr", "dueDate", "2026-04-10", "amount", "4000"),
            Map.of("key", "aug", "dueDate", "2026-08-10", "amount", "4000"),
            Map.of("key", "dec", "dueDate", "2026-12-10", "amount", "4000"));

    assertEquals(new BigDecimal("4000"), InstallmentSchedule.amountFor(portions, "apr"));
    assertFalse(InstallmentSchedule.yearSettled(portions, List.of("apr")));
    assertTrue(InstallmentSchedule.yearSettled(portions, List.of("apr", "aug", "dec")));

    List<Map<String, Object>> next =
        List.of(
            Map.of("key", "apr", "dueDate", "2027-04-10", "amount", "9000"),
            Map.of("key", "aug", "dueDate", "2027-08-10", "amount", "5000"));
    List<Map<String, Object>> revised =
        InstallmentSchedule.reviseUnpaid(
            List.of(Map.of("key", "apr", "dueDate", "2026-04-10", "amount", "4000")), next);

    assertEquals("4000", revised.get(0).get("amount"));
    assertEquals("5000", revised.get(1).get("amount"));
  }
}
