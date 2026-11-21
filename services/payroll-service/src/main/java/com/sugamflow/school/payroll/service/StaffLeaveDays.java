package com.sugamflow.school.payroll.service;

import java.util.List;
import java.util.Map;

/** Unpaid days on a staff attendance sheet. Approved leave is not counted here. */
public final class StaffLeaveDays {

  private StaffLeaveDays() {}

  public static int unpaidDays(Map<String, Object> sheet, String employeeId) {
    if (sheet == null || employeeId == null || employeeId.isBlank()) {
      return 0;
    }
    Object marks = sheet.get("marks");
    if (!(marks instanceof List<?> list)) {
      return 0;
    }
    int days = 0;
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> row)) {
        continue;
      }
      if (!employeeId.equalsIgnoreCase(String.valueOf(row.get("staffId")))) {
        continue;
      }
      if ("ABSENT".equalsIgnoreCase(String.valueOf(row.get("status")))) {
        days++;
      }
    }
    return days;
  }
}
