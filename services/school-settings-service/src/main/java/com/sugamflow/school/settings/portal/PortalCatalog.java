package com.sugamflow.school.settings.portal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default configuration for parent / teacher portal apps (metadata-driven). */
public final class PortalCatalog {

  public static final String PARENT = "parent";
  public static final String TEACHER = "teacher";
  public static final String MODULE_PARENT = "parent_portal";
  public static final String MODULE_TEACHER = "teacher_portal";
  public static final String FLAG_PARENT = "FEATURE_PARENT_APP";
  public static final String FLAG_TEACHER = "FEATURE_TEACHER_APP";

  private PortalCatalog() {}

  public static boolean isKnown(String portalKey) {
    return PARENT.equalsIgnoreCase(portalKey) || TEACHER.equalsIgnoreCase(portalKey);
  }

  public static String moduleKey(String portalKey) {
    return PARENT.equalsIgnoreCase(portalKey) ? MODULE_PARENT : MODULE_TEACHER;
  }

  public static String featureFlag(String portalKey) {
    return PARENT.equalsIgnoreCase(portalKey) ? FLAG_PARENT : FLAG_TEACHER;
  }

  public static Map<String, Object> defaultParentSettings() {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("enabled", true);
    s.put("moduleKey", MODULE_PARENT);
    s.put("requiredFeatureFlag", FLAG_PARENT);
    s.put("title", "Parent App");
    s.put("subtitle", "Attendance, fees, and results for your child");
    s.put("roleCode", "PARENT");
    s.put(
        "profile",
        Map.of(
            "studentName", "Priya Nair",
            "admissionNo", "ADM-1001",
            "className", "Grade 8-A",
            "guardianName", "Parent / Guardian"));
    s.put(
        "summary",
        Map.of(
            "attendancePercent", "94%",
            "pendingFeeAmount", "₹2,500",
            "latestExamLabel", "Unit Test 2 — A",
            "noticesCount", 2));
    s.put("nav", parentNav());
    s.put("widgets", parentWidgets());
    s.put("sections", parentSections());
    s.put("notices", defaultNotices("parent"));
    return s;
  }

  public static Map<String, Object> defaultTeacherSettings() {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("enabled", true);
    s.put("moduleKey", MODULE_TEACHER);
    s.put("requiredFeatureFlag", FLAG_TEACHER);
    s.put("title", "Teacher App");
    s.put("subtitle", "Class roster, attendance, and gradebook");
    s.put("roleCode", "TEACHER");
    s.put(
        "profile",
        Map.of(
            "teacherName", "Ms. Anita Sharma",
            "employeeId", "TCH-204",
            "className", "Grade 8-A",
            "subject", "Mathematics"));
    s.put(
        "summary",
        Map.of(
            "studentsCount", "32",
            "attendanceToday", "28 / 32",
            "pendingMarks", "Unit Test 2",
            "noticesCount", 1));
    s.put("nav", teacherNav());
    s.put("widgets", teacherWidgets());
    s.put("sections", teacherSections());
    s.put("notices", defaultNotices("teacher"));
    return s;
  }

  private static List<Map<String, Object>> parentNav() {
    List<Map<String, Object>> nav = new ArrayList<>();
    nav.add(navItem("home", "Home", "/parent", null));
    nav.add(navItem("attendance", "Attendance", "/parent/attendance", "FEATURE_ATTENDANCE"));
    nav.add(navItem("fees", "Fees", "/parent/fees", "FEATURE_FEE"));
    nav.add(navItem("exams", "Results", "/parent/exams", "FEATURE_EXAM"));
    return nav;
  }

  private static List<Map<String, Object>> teacherNav() {
    List<Map<String, Object>> nav = new ArrayList<>();
    nav.add(navItem("home", "Home", "/teacher", null));
    nav.add(navItem("students", "Students", "/teacher/students", "FEATURE_STUDENT_MASTER"));
    nav.add(navItem("attendance", "Attendance", "/teacher/attendance", "FEATURE_ATTENDANCE"));
    nav.add(navItem("gradebook", "Gradebook", "/teacher/gradebook", "FEATURE_EXAM"));
    return nav;
  }

  private static Map<String, Object> navItem(
      String id, String label, String route, String flag) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("label", label);
    m.put("route", route);
    if (flag != null) {
      m.put("requiredFeatureFlag", flag);
    }
    return m;
  }

  private static List<Map<String, Object>> parentWidgets() {
    List<Map<String, Object>> w = new ArrayList<>();
    w.add(stat("attendance_summary", "Attendance", "attendancePercent", "/parent/attendance", 1));
    w.add(stat("fee_pending", "Pending fees", "pendingFeeAmount", "/parent/fees", 2));
    w.add(stat("latest_result", "Latest result", "latestExamLabel", "/parent/exams", 3));
    w.add(listWidget("notices", "Notices", 4));
    return w;
  }

  private static List<Map<String, Object>> teacherWidgets() {
    List<Map<String, Object>> w = new ArrayList<>();
    w.add(stat("class_size", "Students", "studentsCount", "/teacher/students", 1));
    w.add(stat("attendance_today", "Today", "attendanceToday", "/teacher/attendance", 2));
    w.add(stat("pending_marks", "Marks due", "pendingMarks", "/teacher/gradebook", 3));
    w.add(listWidget("notices", "Notices", 4));
    return w;
  }

  private static Map<String, Object> stat(
      String id, String title, String valueKey, String route, int order) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("type", "stat");
    m.put("title", title);
    m.put("valueKey", valueKey);
    m.put("route", route);
    m.put("order", order);
    m.put("enabled", true);
    return m;
  }

  private static Map<String, Object> listWidget(String id, String title, int order) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("type", "list");
    m.put("title", title);
    m.put("order", order);
    m.put("enabled", true);
    return m;
  }

  private static Map<String, Object> parentSections() {
    Map<String, Object> sections = new LinkedHashMap<>();
    sections.put(
        "attendance",
        section("Attendance", "/api/attendance/records", "No attendance records yet."));
    sections.put("fees", section("Fee payments", "/api/fee/records", "No fee records yet."));
    sections.put("exams", section("Exam results", "/api/exam/records", "No exam records yet."));
    return sections;
  }

  private static Map<String, Object> teacherSections() {
    Map<String, Object> sections = new LinkedHashMap<>();
    sections.put(
        "students",
        section("Class roster", "/api/student/students", "No students enrolled yet."));
    sections.put(
        "attendance",
        section("Attendance inbox", "/api/attendance/records", "No attendance records yet."));
    sections.put(
        "gradebook", section("Gradebook", "/api/exam/records", "No exam records yet."));
    return sections;
  }

  private static Map<String, Object> section(String title, String apiPath, String emptyMessage) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", title);
    m.put("apiPath", apiPath);
    m.put("emptyMessage", emptyMessage);
    return m;
  }

  private static List<Map<String, Object>> defaultNotices(String audience) {
    List<Map<String, Object>> notices = new ArrayList<>();
    Map<String, Object> n1 = new LinkedHashMap<>();
    n1.put("id", "n1");
    n1.put("title", "Parent".equalsIgnoreCase(audience) ? "PTM next Friday" : "Submit unit marks");
    n1.put(
        "body",
        "Parent".equalsIgnoreCase(audience)
            ? "Parent-teacher meeting for Grade 8 on Friday 3pm."
            : "Please finalize Unit Test 2 marks by Friday.");
    notices.add(n1);
    Map<String, Object> n2 = new LinkedHashMap<>();
    n2.put("id", "n2");
    n2.put("title", "Holiday notice");
    n2.put("body", "School closed on Monday for regional holiday.");
    notices.add(n2);
    return notices;
  }
}
