package com.sugamflow.school.student.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default academic lifecycle configuration — no school-specific hardcoding. */
public final class LifecycleCatalog {

  public static final String TYPE_PROMOTION_MAP = "PROMOTION_MAP";
  public static final String TYPE_STATUS_POLICY = "STATUS_POLICY";
  public static final String TYPE_TC_POLICY = "TC_POLICY";
  public static final String TYPE_ACADEMIC_SESSION = "ACADEMIC_SESSION";

  public static final String FEATURE_ACADEMIC_LIFECYCLE = "FEATURE_ACADEMIC_LIFECYCLE";

  public static final String EVENT_PROMOTE = "PROMOTE";
  public static final String EVENT_ROLLOVER = "ROLLOVER";
  public static final String EVENT_TC = "TC_ISSUE";
  public static final String EVENT_DROPOUT = "DROPOUT";
  public static final String EVENT_ALUMNI = "ALUMNI";

  public static final String ACTION_BLOCK_TC = "BLOCK_TC";

  private LifecycleCatalog() {}

  public static List<Map<String, Object>> defaultPromotionMaps() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("definitionKey", "default_grade_map");
    m.put("name", "Default grade promotion map");
    m.put(
        "mappings",
        Map.of(
            "Grade 7-A", "Grade 8-A",
            "Grade 8-A", "Grade 9-A",
            "Grade 9-A", "Grade 10-A",
            "Grade 10-A", "Grade 11-A",
            "Grade 11-A", "Grade 12-A"));
    m.put("notes", "Customize via Academic Lifecycle admin — keys match classField answers.");
    list.add(m);
    return list;
  }

  public static List<Map<String, Object>> defaultStatusPolicies() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("definitionKey", "default_statuses");
    p.put("name", "Default student status policy");
    p.put(
        "allowedStatuses",
        List.of("ACTIVE", "PROMOTED", "TRANSFERRED", "DROPOUT", "ALUMNI", "TC_ISSUED"));
    p.put("activeStatus", "ACTIVE");
    p.put("promotedStatus", "ACTIVE");
    p.put("tcStatus", "TRANSFERRED");
    p.put("dropoutStatus", "DROPOUT");
    p.put("alumniStatus", "ALUMNI");
    p.put(
        "terminalStatuses",
        List.of("TRANSFERRED", "DROPOUT", "ALUMNI", "TC_ISSUED"));
    list.add(p);
    return list;
  }

  public static List<Map<String, Object>> defaultTcPolicies() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("definitionKey", "default_tc");
    t.put("name", "Default transfer certificate policy");
    t.put("templateKey", "transfer_certificate");
    t.put("requiredFeatureFlag", "FEATURE_REPORT_BUILDER");
    t.put("blockIfTerminal", true);
    t.put("clearanceEnabled", true);
    t.put("clearanceProviders", List.of("fee", "library"));
    t.put("blockActions", List.of(ACTION_BLOCK_TC));
    t.put("notes", "TC PDF via Report Designer; fee/library clearance via Rule Engine (BLOCK_TC).");
    list.add(t);
    return list;
  }

  public static List<Map<String, Object>> defaultSessions() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(session("2025-26", "Academic Year 2025-26", "2026-27", true));
    list.add(session("2026-27", "Academic Year 2026-27", null, false));
    return list;
  }

  private static Map<String, Object> session(
      String key, String name, String nextKey, boolean current) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("definitionKey", key);
    s.put("name", name);
    s.put("nextSessionKey", nextKey);
    s.put("current", current);
    return s;
  }
}
