package com.sugamflow.school.settings.offline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default offline-mode configuration (allow-listed entity types + cache keys). */
public final class OfflineCatalog {

  public static final String MODULE_KEY = "offline";
  public static final String FEATURE_OFFLINE_MODE = "FEATURE_OFFLINE_MODE";

  private OfflineCatalog() {}

  public static Map<String, Object> defaultSettings() {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("enabled", true);
    s.put("moduleKey", MODULE_KEY);
    s.put("requiredFeatureFlag", FEATURE_OFFLINE_MODE);
    s.put("maxQueueSize", 200);
    s.put("syncBatchSize", 25);
    s.put("autoSyncOnReconnect", true);
    s.put("cacheTtlMinutes", 1440);
    s.put("allowedEntityTypes", defaultEntityTypes());
    s.put("cacheKeys", defaultCacheKeys());
    s.put(
        "notes",
        "Client queues mutations while offline; sync pushes allow-listed operations when online.");
    return s;
  }

  public static List<Map<String, Object>> defaultEntityTypes() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(entity("ATTENDANCE_MARK", "POST", "/api/attendance/records", "FEATURE_ATTENDANCE"));
    list.add(entity("FEE_PAYMENT", "POST", "/api/fee/collections", "FEATURE_FEE"));
    list.add(entity("EXAM_MARKS", "POST", "/api/exam/records", "FEATURE_EXAM"));
    list.add(entity("LIBRARY_ISSUE", "POST", "/api/library/records", "FEATURE_LIBRARY"));
    return list;
  }

  public static List<Map<String, Object>> defaultCacheKeys() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(cache("forms.attendance_mark", "GET", "/api/forms/attendance_mark"));
    list.add(cache("forms.fee_collection", "GET", "/api/forms/fee_collection"));
    list.add(cache("modules.attendance", "GET", "/api/config/modules/attendance"));
    list.add(cache("branches", "GET", "/api/config/branches"));
    list.add(cache("theme", "GET", "/api/config/design-studio/theme"));
    return list;
  }

  private static Map<String, Object> entity(
      String type, String method, String path, String flag) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("entityType", type);
    m.put("method", method);
    m.put("path", path);
    m.put("requiredFeatureFlag", flag);
    m.put("enabled", true);
    return m;
  }

  private static Map<String, Object> cache(String key, String method, String path) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("method", method);
    m.put("path", path);
    return m;
  }
}
