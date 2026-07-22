package com.sugamflow.school.student.directory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration defaults for the Student Directory — columns, filters, actions, and summary
 * widgets are metadata-driven so schools customize without code changes.
 */
public final class DirectoryCatalog {

  private DirectoryCatalog() {}

  public static Map<String, Object> defaultBootstrap() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("directoryKey", "student_directory");
    out.put("title", "Student Directory");
    out.put("requiredFeatureFlag", "FEATURE_STUDENT_MASTER");
    out.put("columns", defaultColumns());
    out.put("filters", defaultFilters());
    out.put("searchFields", defaultSearchFields());
    out.put("quickActions", defaultQuickActions());
    out.put("bulkActions", defaultBulkActions());
    out.put("summaryWidgets", defaultSummaryWidgets());
    out.put("exportFormats", List.of("CSV", "PDF", "EXCEL"));
    out.put("pageSizeOptions", List.of(25, 50, 100, 200));
    out.put("defaultPageSize", 50);
    return out;
  }

  public static List<Map<String, Object>> defaultColumns() {
    return List.of(
        col("admissionNo", "Admission No", true, true),
        col("fullName", "Student Name", true, true),
        col("classSection", "Class / Section", true, true),
        col("gender", "Gender", true, true),
        col("status", "Status", true, true),
        col("mobile", "Mobile", true, false),
        col("branchId", "Branch", true, true),
        col("academicSessionId", "Session", true, false),
        col("category", "Category", false, true),
        col("house", "House", false, true),
        col("parentName", "Parent", false, false),
        col("transport", "Transport", false, true),
        col("hostel", "Hostel", false, true));
  }

  public static List<Map<String, Object>> defaultFilters() {
    return List.of(
        filter("status", "Admission Status", "SELECT", List.of("ACTIVE", "INACTIVE", "ALUMNI", "TC_ISSUED", "DROPOUT")),
        filter("classSection", "Class / Section", "TEXT", List.of()),
        filter("gender", "Gender", "SELECT", List.of("Male", "Female", "Other")),
        filter("category", "Category", "TEXT", List.of()),
        filter("house", "House", "TEXT", List.of()),
        filter("transport", "Transport Students", "BOOLEAN", List.of()),
        filter("hostel", "Hostel Students", "BOOLEAN", List.of()),
        filter("scholarship", "Scholarship", "BOOLEAN", List.of()),
        filter("feePending", "Fee Pending", "BOOLEAN", List.of()));
  }

  public static List<Map<String, Object>> defaultSearchFields() {
    return List.of(
        Map.of("key", "q", "label", "Global Search"),
        Map.of("key", "fullName", "label", "Student Name"),
        Map.of("key", "admissionNo", "label", "Admission Number"),
        Map.of("key", "rollNo", "label", "Roll Number"),
        Map.of("key", "mobile", "label", "Mobile Number"),
        Map.of("key", "parentName", "label", "Parent Name"),
        Map.of("key", "email", "label", "Email"),
        Map.of("key", "aadhaar", "label", "Aadhaar / Student ID"),
        Map.of("key", "rfid", "label", "RFID / Card Number"));
  }

  public static List<Map<String, Object>> defaultQuickActions() {
    return List.of(
        action("profile", "Open Profile", "/admin/students", "FEATURE_STUDENT_MASTER"),
        action("promote", "Promote", "/admin/lifecycle", "FEATURE_ACADEMIC_LIFECYCLE"),
        action("tc", "Issue TC", "/admin/lifecycle", "FEATURE_ACADEMIC_LIFECYCLE"),
        action("fee", "Fee Collection", "/admin/fee", "FEATURE_FEE"),
        action("attendance", "Attendance", "/admin/attendance", "FEATURE_ATTENDANCE"),
        action("exam", "Result", "/admin/exam", "FEATURE_EXAM"),
        action("exportCsv", "Export CSV", null, "FEATURE_STUDENT_MASTER"));
  }

  public static List<Map<String, Object>> defaultBulkActions() {
    return List.of(
        Map.of("key", "exportCsv", "label", "Export Selected / Filtered", "permission", "EXPORT"),
        Map.of("key", "softDelete", "label", "Soft Delete", "permission", "DELETE"),
        Map.of("key", "restore", "label", "Restore", "permission", "RESTORE"),
        Map.of("key", "sendEmail", "label", "Send Email", "permission", "COMMUNICATE"),
        Map.of("key", "promote", "label", "Promote", "permission", "LIFECYCLE"));
  }

  public static List<Map<String, Object>> defaultSummaryWidgets() {
    return List.of(
        widget("total", "Total Students"),
        widget("active", "Active"),
        widget("alumni", "Alumni"),
        widget("tcIssued", "TC Issued"),
        widget("boys", "Boys"),
        widget("girls", "Girls"),
        widget("newAdmissions", "New (30 days)"),
        widget("byClass", "Class-wise"),
        widget("byBranch", "Branch-wise"));
  }

  private static Map<String, Object> col(String key, String label, boolean visible, boolean sortable) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("label", label);
    m.put("visible", visible);
    m.put("sortable", sortable);
    return m;
  }

  private static Map<String, Object> filter(
      String key, String label, String type, List<String> options) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("label", label);
    m.put("type", type);
    m.put("options", options);
    return m;
  }

  private static Map<String, Object> action(String key, String label, String route, String feature) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("label", label);
    if (route != null) {
      m.put("route", route);
    }
    m.put("requiredFeatureFlag", feature);
    return m;
  }

  private static Map<String, Object> widget(String key, String label) {
    return Map.of("key", key, "label", label);
  }
}
