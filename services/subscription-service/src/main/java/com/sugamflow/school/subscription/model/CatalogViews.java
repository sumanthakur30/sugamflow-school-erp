package com.sugamflow.school.subscription.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only catalog DTOs for platform subscription definitions. */
public final class CatalogViews {

  private CatalogViews() {}

  public static Map<String, Object> businessType(
      String code, String name, String description, int sortOrder) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code);
    m.put("name", name);
    m.put("description", description);
    m.put("sortOrder", sortOrder);
    return m;
  }

  public static Map<String, Object> module(
      String code, String businessTypeCode, String name, String description, int sortOrder) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code);
    m.put("businessTypeCode", businessTypeCode);
    m.put("name", name);
    m.put("description", description);
    m.put("sortOrder", sortOrder);
    return m;
  }

  public static Map<String, Object> feature(
      String code, String moduleCode, String name, String description, int sortOrder) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code);
    m.put("moduleCode", moduleCode);
    m.put("name", name);
    m.put("description", description);
    m.put("sortOrder", sortOrder);
    return m;
  }

  public static Map<String, Object> limit(
      String code,
      String businessTypeCode,
      String name,
      String unit,
      String aggregation,
      String description,
      int sortOrder) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code);
    m.put("businessTypeCode", businessTypeCode);
    m.put("name", name);
    m.put("unit", unit);
    m.put("aggregation", aggregation);
    m.put("description", description);
    m.put("sortOrder", sortOrder);
    return m;
  }
}
