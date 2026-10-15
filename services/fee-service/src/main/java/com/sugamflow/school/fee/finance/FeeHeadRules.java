package com.sugamflow.school.fee.finance;

import com.sugamflow.school.fee.web.FeeException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tenant fee-head names must be unique. The same label cannot be saved under another code. */
public final class FeeHeadRules {

  private FeeHeadRules() {}

  public static void validateUniqueLabel(String key, String label, List<Map<String, Object>> activeHeads) {
    String name = normalize(label);
    if (name.isEmpty()) {
      throw new FeeException("VALIDATION", "Fee head name is required");
    }
    String code = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    for (Map<String, Object> head : activeHeads) {
      String otherKey = string(head.get("definitionKey"));
      if (otherKey.isEmpty()) {
        otherKey = string(head.get("code"));
      }
      if (otherKey.equalsIgnoreCase(code)) {
        continue;
      }
      if (normalize(string(head.get("label"))).equals(name)) {
        throw new FeeException("VALIDATION", "A fee head named \"" + label.trim() + "\" already exists");
      }
    }
  }

  static String normalize(String label) {
    if (label == null) {
      return "";
    }
    return label.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static String string(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
