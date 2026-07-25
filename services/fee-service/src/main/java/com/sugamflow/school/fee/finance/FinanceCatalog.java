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
  public static final String TYPE_LATE_FEE_POLICY = "LATE_FEE_POLICY";
  public static final String TYPE_PAYMENT_PROVIDER = "PAYMENT_PROVIDER";

  public static final String FEATURE_MULTI_PAYMENT_GATEWAY = "FEATURE_MULTI_PAYMENT_GATEWAY";
  public static final String FEATURE_ACCOUNTING = "FEATURE_ACCOUNTING";

  /** Billing frequencies used on heads / structure lines. */
  public static final String FREQ_MONTHLY = "M";
  public static final String FREQ_QUARTERLY = "Q";
  public static final String FREQ_HALF_YEARLY = "HY";
  public static final String FREQ_YEARLY = "Y";
  public static final String FREQ_ONE_TIME = "OT";

  private FinanceCatalog() {}

  public static List<Map<String, Object>> defaultHeads() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(head("TUITION", "Tuition Fee", "ACADEMIC", true, FREQ_MONTHLY, 0, true));
    list.add(head("ADMISSION", "Admission Fee", "ACADEMIC", false, FREQ_ONE_TIME, 0, true));
    list.add(head("ANNUAL", "Annual Fee", "ACADEMIC", false, FREQ_YEARLY, 0, true));
    list.add(head("TRANSPORT", "Transport Fee", "TRANSPORT", true, FREQ_MONTHLY, 0, true));
    list.add(head("HOSTEL", "Hostel Fee", "HOSTEL", true, FREQ_MONTHLY, 0, true));
    list.add(head("LIBRARY", "Library Fee", "ACADEMIC", false, FREQ_YEARLY, 0, false));
    list.add(head("EXAM", "Examination Fee", "ACADEMIC", false, FREQ_HALF_YEARLY, 0, false));
    list.add(head("COMPUTER", "Computer / Lab Fee", "ACADEMIC", false, FREQ_QUARTERLY, 18, true));
    list.add(head("SPORTS", "Sports Fee", "OPTIONAL", false, FREQ_YEARLY, 0, false));
    list.add(head("DEVELOPMENT", "Development Fee", "ACADEMIC", false, FREQ_YEARLY, 0, false));
    list.add(head("LAB", "Science Lab Fee", "ACADEMIC", false, FREQ_YEARLY, 18, true));
    list.add(head("SECURITY", "Security Deposit", "OTHER", true, FREQ_ONE_TIME, 0, false));
    list.add(head("MISC", "Miscellaneous Fee", "OTHER", true, FREQ_ONE_TIME, 0, true));
    list.add(head("LATE_FEE", "Late Fee / Fine", "FINE", false, FREQ_ONE_TIME, 0, false));
    return list;
  }

  public static List<Map<String, Object>> defaultStructures() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> annual = new LinkedHashMap<>();
    annual.put("definitionKey", "grade_8_annual");
    annual.put("name", "Grade 8 Annual");
    annual.put("eligibility", Map.of("classSection", "Grade 8-A", "academicSessionId", "2025-26"));
    annual.put(
        "lines",
        List.of(
            line("ANNUAL", 5000, FREQ_YEARLY),
            line("TUITION", 12000, FREQ_MONTHLY),
            line("LIBRARY", 500, FREQ_YEARLY),
            line("EXAM", 800, FREQ_HALF_YEARLY),
            line("COMPUTER", 1500, FREQ_QUARTERLY),
            line("SPORTS", 400, FREQ_YEARLY),
            line("DEVELOPMENT", 1000, FREQ_YEARLY)));
    annual.put("currency", "INR");
    annual.put("notes", "Default annual pack — customize via Finance admin. Hostel/Transport add-ons from ops.");
    list.add(annual);

    Map<String, Object> admission = new LinkedHashMap<>();
    admission.put("definitionKey", "admission_pack");
    admission.put("name", "New Admission Pack");
    admission.put("eligibility", Map.of());
    admission.put(
        "lines",
        List.of(
            line("ADMISSION", 10000, FREQ_ONE_TIME),
            line("SECURITY", 2000, FREQ_ONE_TIME),
            line("ANNUAL", 5000, FREQ_YEARLY),
            line("MISC", 500, FREQ_ONE_TIME)));
    admission.put("currency", "INR");
    admission.put("notes", "One-time admission + annual components.");
    list.add(admission);
    return list;
  }

  public static List<Map<String, Object>> defaultConcessions() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> sibling = new LinkedHashMap<>();
    sibling.put("definitionKey", "sibling_10");
    sibling.put("name", "Sibling concession 10%");
    sibling.put("benefit", Map.of("type", "PERCENT", "value", 10, "capAmount", 2000));
    sibling.put("appliesToHeads", List.of("TUITION", "ANNUAL"));
    sibling.put("requiredRuleAction", "APPLY_SIBLING_CONCESSION");
    list.add(sibling);

    Map<String, Object> scholarship = new LinkedHashMap<>();
    scholarship.put("definitionKey", "scholarship_50");
    scholarship.put("name", "Scholarship 50% (tuition)");
    scholarship.put("benefit", Map.of("type", "PERCENT", "value", 50, "capAmount", 0));
    scholarship.put("appliesToHeads", List.of("TUITION"));
    scholarship.put("requiredStudentFlag", "scholarship");
    scholarship.put("requiredRuleAction", "APPLY_SCHOLARSHIP");
    list.add(scholarship);
    return list;
  }

  public static List<Map<String, Object>> defaultLateFeePolicies() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("definitionKey", "late_per_day_10");
    p.put("name", "₹10/day after 7-day grace");
    p.put("graceDays", 7);
    p.put("perDayAmount", 10);
    p.put("flatAmount", 0);
    p.put("capAmount", 500);
    p.put("appliesToHeads", List.of("TUITION", "ANNUAL", "TRANSPORT", "HOSTEL"));
    p.put("enabled", true);
    list.add(p);
    return list;
  }

  public static List<Map<String, Object>> defaultProviders() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> simulated = new LinkedHashMap<>();
    simulated.put("definitionKey", "simulated");
    simulated.put("name", "Simulated Gateway");
    simulated.put("adapter", "SIMULATED");
    simulated.put("enabledModes", List.of("UPI", "CARD", "NETBANKING"));
    simulated.put("requiredFeatureFlag", FEATURE_MULTI_PAYMENT_GATEWAY);
    simulated.put("notes", "Dev/demo provider — capture via simulate-capture API.");
    list.add(simulated);

    Map<String, Object> razorpay = new LinkedHashMap<>();
    razorpay.put("definitionKey", "razorpay");
    razorpay.put("name", "Razorpay");
    razorpay.put("adapter", "RAZORPAY");
    razorpay.put("enabledModes", List.of("UPI", "CARD", "NETBANKING"));
    razorpay.put("requiredFeatureFlag", FEATURE_MULTI_PAYMENT_GATEWAY);
    razorpay.put(
        "notes",
        "Production India gateway. Requires fee.payment.razorpay.key-id / key-secret / webhook-secret.");
    list.add(razorpay);
    return list;
  }

  private static Map<String, Object> head(
      String key,
      String label,
      String category,
      boolean refundable,
      String frequency,
      double gstRate,
      boolean taxable) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("definitionKey", key);
    m.put("code", key);
    m.put("label", label);
    m.put("category", category);
    m.put("refundable", refundable);
    m.put("frequency", frequency);
    m.put("gstRate", gstRate);
    m.put("taxable", taxable && gstRate > 0);
    m.put("hsnCode", "");
    m.put("enabled", true);
    return m;
  }

  private static Map<String, Object> line(String headKey, double amount, String frequency) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("headKey", headKey);
    m.put("amount", amount);
    m.put("frequency", frequency);
    m.put("optional", false);
    return m;
  }
}
