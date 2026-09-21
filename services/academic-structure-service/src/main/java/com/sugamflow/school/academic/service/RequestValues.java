package com.sugamflow.school.academic.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small null-safe readers for untyped request bodies. */
final class RequestValues {

  private RequestValues() {}

  static String str(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() ? null : s;
  }

  static String strOr(Map<String, Object> body, String key, String fallback) {
    String v = str(body, key);
    return v == null ? fallback : v;
  }

  static Integer intOrNull(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    if (v instanceof Number n) {
      return n.intValue();
    }
    String s = str(body, key);
    if (s == null) {
      return null;
    }
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  static int intOr(Map<String, Object> body, String key, int fallback) {
    Integer v = intOrNull(body, key);
    return v == null ? fallback : v;
  }

  static boolean bool(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    if (v instanceof Boolean b) {
      return b;
    }
    return "true".equalsIgnoreCase(String.valueOf(v));
  }

  static UUID uuid(Map<String, Object> body, String key) {
    String s = str(body, key);
    if (s == null) {
      return null;
    }
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    if (v instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    return new LinkedHashMap<>();
  }
}
