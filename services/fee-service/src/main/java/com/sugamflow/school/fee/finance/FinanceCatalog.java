package com.sugamflow.school.fee.finance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default finance configuration (heads, structures, providers) — no school-specific hardcoding. */
public final class FinanceCatalog {

  public static final String TYPE_FEE_HEAD = "FEE_HEAD";
  public static final String TYPE_FEE_STRUCTURE = "FEE_STRUCTURE";
  public static final String TYPE_CONCESSION = "CONCESSION_POLICY";
  public static final String TYPE_PAYMENT_PROVIDER = "PAYMENT_PROVIDER";

  public static final String FEATURE_MULTI_PAYMENT_GATEWAY = "FEATURE_MULTI_PAYMENT_GATEWAY";
  public static final String FEATURE_ACCOUNTING = "FEATURE_ACCOUNTING";

  private FinanceCatalog() {}

  public static List<Map<String, Object>> defaultHeads() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(head("TUITION", "Tuition Fee", "ACADEMIC", true));
    list.add(head("TRANSPORT", "Transport Fee", "TRANSPORT", true));
    list.add(head("LIBRARY", "Library Fee", "ACADEMIC", false));
    list.add(head("EXAM", "Examination Fee", "ACADEMIC", false));
    return list;
  }

  public static List<Map<String, Object>> defaultStructures() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("definitionKey", "grade_8_annual");
    s.put("name", "Grade 8 Annual");
    s.put("eligibility", Map.of("classSection", "Grade 8-A", "academicSessionId", "2025-26"));
    s.put(
        "lines",
        List.of(
            line("TUITION", 12000),
            line("LIBRARY", 500),
            line("EXAM", 800)));
    s.put("currency", "INR");
    s.put("notes", "Default structure — customize via Finance admin.");
    list.add(s);
    return list;
  }

  public static List<Map<String, Object>> defaultConcessions() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("definitionKey", "sibling_10");
    c.put("name", "Sibling concession 10%");
    c.put("benefit", Map.of("type", "PERCENT", "value", 10, "capAmount", 2000));
    c.put("appliesToHeads", List.of("TUITION"));
    c.put("requiredRuleAction", "APPLY_SIBLING_CONCESSION");
    list.add(c);
    return list;
  }

  public static List<Map<String, Object>> defaultProviders() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("definitionKey", "simulated");
    p.put("name", "Simulated Gateway");
    p.put("adapter", "SIMULATED");
    p.put("enabledModes", List.of("UPI", "CARD", "NETBANKING"));
    p.put("requiredFeatureFlag", FEATURE_MULTI_PAYMENT_GATEWAY);
    p.put("notes", "Dev/demo provider — replace with Razorpay/Stripe adapter via config.");
    list.add(p);
    return list;
  }

  private static Map<String, Object> head(
      String key, String label, String category, boolean refundable) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("definitionKey", key);
    m.put("code", key);
    m.put("label", label);
    m.put("category", category);
    m.put("refundable", refundable);
    m.put("enabled", true);
    return m;
  }

  private static Map<String, Object> line(String headKey, double amount) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("headKey", headKey);
    m.put("amount", amount);
    m.put("optional", false);
    return m;
  }
}
