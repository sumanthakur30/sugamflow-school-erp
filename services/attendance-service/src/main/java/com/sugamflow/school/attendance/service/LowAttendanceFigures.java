package com.sugamflow.school.attendance.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The columns the low-attendance list shows. No other figure is added. */
public final class LowAttendanceFigures {

  private LowAttendanceFigures() {}

  public static List<Map<String, Object>> fromDesk(List<Map<String, Object>> deskRows) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (deskRows == null) {
      return out;
    }
    for (Map<String, Object> row : deskRows) {
      if (row == null) {
        continue;
      }
      Map<String, Object> figure = new LinkedHashMap<>();
      figure.put("studentName", text(row.get("studentName")));
      figure.put("admissionNo", text(row.get("admissionNo")));
      figure.put("status", text(row.get("status")));
      figure.put("percent", row.get("percent"));
      out.add(figure);
    }
    return out;
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
