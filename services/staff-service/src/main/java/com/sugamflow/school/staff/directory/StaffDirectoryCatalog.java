package com.sugamflow.school.staff.directory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration defaults for the Staff Directory — columns, filters, actions, and summary
 * widgets are metadata-driven so schools customize without code changes.
 */
public final class StaffDirectoryCatalog {

  private StaffDirectoryCatalog() {}

  public static Map<String, Object> defaultBootstrap() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("directoryKey", "staff_directory");
    out.put("title", "Staff Directory");
    out.put("requiredFeatureFlag", "FEATURE_STAFF_MASTER");
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
        col("employeeNo", "Employee No", true, true),
        col("fullName", "Staff Name", true, true),
        col("department", "Department", true, true),
        col("designation", "Designation", true, true),
        col("employmentType", "Employment Type", true, true),
        col("gender", "Gender", false, true),
        col("status", "Status", true, true),
        col("mobile", "Mobile", true, false),
        col("email", "Email", false, false),
        col("branchId", "Branch", true, true),
        col("joiningDate", "Joining Date", false, true));
  }

  public static List<Map<String, Object>> defaultFilters() {
    return List.of(
        filter("status", "Employment Status", "SELECT", List.of("ACTIVE", "INACTIVE", "RELIEVED", "ON_LEAVE")),
        filter("department", "Department", "TEXT", List.of()),
        filter("designation", "Designation", "TEXT", List.of()),
        filter(
            "employmentType",
            "Employment Type",
            "SELECT",
            List.of("PERMANENT", "CONTRACT", "PART_TIME", "PROBATION")),
        filter("gender", "Gender", "SELECT", List.of("Male", "Female", "Other")));
  }

  public static List<Map<String, Object>> defaultSearchFields() {
    return List.of(
        Map.of("key", "q", "label", "Global Search"),
        Map.of("key", "fullName", "label", "Staff Name"),
        Map.of("key", "employeeNo", "label", "Employee Number"),
        Map.of("key", "mobile", "label", "Mobile Number"),
        Map.of("key", "email", "label", "Email"));
  }

  public static List<Map<String, Object>> defaultQuickActions() {
    return List.of(
        action("profile", "Open Profile", "/admin/staff", "FEATURE_STAFF_MASTER"),
        action("payroll", "Payroll", "/admin/payroll", "FEATURE_PAYROLL"),
        action("attendance", "Attendance", "/admin/attendance", "FEATURE_ATTENDANCE"),
        action("exportCsv", "Export CSV", null, "FEATURE_STAFF_MASTER"));
  }

  public static List<Map<String, Object>> defaultBulkActions() {
    return List.of(
        Map.of("key", "exportCsv", "label", "Export Selected / Filtered", "permission", "EXPORT"),
        Map.of("key", "sendEmail", "label", "Send Email", "permission", "COMMUNICATE"));
  }

  public static List<Map<String, Object>> defaultSummaryWidgets() {
    return List.of(
        widget("total", "Total Staff"),
        widget("active", "Active"),
        widget("inactive", "Inactive"),
        widget("male", "Male"),
        widget("female", "Female"),
        widget("newJoinees", "New (30 days)"),
        widget("byDepartment", "Department-wise"),
        widget("byDesignation", "Designation-wise"),
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
