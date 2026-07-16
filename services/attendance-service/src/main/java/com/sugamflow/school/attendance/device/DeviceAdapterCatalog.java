package com.sugamflow.school.attendance.device;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalog of pluggable attendance device adapters (metadata only — no vendor SDKs). */
public final class DeviceAdapterCatalog {

  public static final String TYPE_BIOMETRIC = "BIOMETRIC";
  public static final String TYPE_FACE = "FACE";
  public static final String TYPE_GPS = "GPS";
  public static final String TYPE_AI_CAMERA = "AI_CAMERA";

  private DeviceAdapterCatalog() {}

  public static List<Map<String, Object>> adapterTypes() {
    return List.of(
        type(TYPE_BIOMETRIC, "Biometric fingerprint / card", "FEATURE_BIOMETRIC"),
        type(TYPE_FACE, "Face recognition terminal", "FEATURE_FACE_RECOGNITION"),
        type(TYPE_GPS, "GPS / geo-fence check-in", "FEATURE_GPS"),
        type(TYPE_AI_CAMERA, "AI camera attendance", "FEATURE_AI"));
  }

  public static String requiredFlag(String adapterType) {
    return adapterTypes().stream()
        .filter(t -> adapterType.equalsIgnoreCase(String.valueOf(t.get("type"))))
        .map(t -> String.valueOf(t.get("requiredFeatureFlag")))
        .findFirst()
        .orElse("FEATURE_ATTENDANCE");
  }

  public static boolean isKnown(String adapterType) {
    return adapterTypes().stream()
        .anyMatch(t -> adapterType.equalsIgnoreCase(String.valueOf(t.get("type"))));
  }

  private static Map<String, Object> type(String type, String label, String flag) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("label", label);
    m.put("requiredFeatureFlag", flag);
    m.put("simulated", true);
    return m;
  }
}
