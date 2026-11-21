package com.sugamflow.school.fee.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The columns the fee desk shows for an open due. No other figure is added. */
public final class OutstandingFeeFigures {

  private OutstandingFeeFigures() {}

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
      figure.put("classSection", text(row.get("classSection")));
      figure.put("feeHead", text(row.get("feeHead")));
      figure.put("dueDate", row.get("dueDate"));
      figure.put("amount", row.get("amount"));
      out.add(figure);
    }
    return out;
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
