package com.sugamflow.school.payroll.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaffLeaveDaysTest {

  @Test
  void unpaidAbsenceDaysAppearAndApprovedLeaveDoesNot() {
    Map<String, Object> sheet =
        Map.of(
            "marks",
            List.of(
                Map.of("staffId", "T-1", "status", "ABSENT"),
                Map.of("staffId", "T-1", "status", "LEAVE"),
                Map.of("staffId", "T-1", "status", "ABSENT"),
                Map.of("staffId", "T-2", "status", "ABSENT")));

    assertEquals(2, StaffLeaveDays.unpaidDays(sheet, "T-1"));
  }
}
