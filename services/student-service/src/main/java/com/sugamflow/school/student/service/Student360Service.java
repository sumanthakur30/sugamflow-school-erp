package com.sugamflow.school.student.service;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.integration.DomainSnapshotClient;
import com.sugamflow.school.student.web.StudentException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student 360 — single profile that joins master data with fee/library clearance, lifecycle,
 * and best-effort attendance / exam snapshots.
 */
@Service
public class Student360Service {

  public static final String FEATURE_STUDENT_MASTER = StudentRecordService.FEATURE_STUDENT_MASTER;

  private final StudentRecordService records;
  private final LifecycleService lifecycle;
  private final RelationshipAccessService relationshipAccess;
  private final ConfigEngineClient engines;
  private final DomainSnapshotClient snapshots;

  public Student360Service(
      StudentRecordService records,
      LifecycleService lifecycle,
      RelationshipAccessService relationshipAccess,
      ConfigEngineClient engines,
      DomainSnapshotClient snapshots) {
    this.records = records;
    this.lifecycle = lifecycle;
    this.relationshipAccess = relationshipAccess;
    this.engines = engines;
    this.snapshots = snapshots;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> profile(UUID id) {
    TenantScope scope = TenantContext.require();
    if (!engines.isFeatureEnabled(scope, FEATURE_STUDENT_MASTER)) {
      throw new StudentException(
          "FEATURE_DISABLED", "FEATURE_STUDENT_MASTER is off for this subscription plan.");
    }
    Map<String, Object> student = records.get(id);
    AccessScope access = relationshipAccess.resolve(scope);
    if (!access.allowsStudentDto(student)) {
      throw new StudentException("NOT_FOUND", "Student not found");
    }

    String admissionNo =
        firstNonBlank(string(student.get("admissionNo")), string(answers(student).get("admissionNo")));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("student", student);
    out.put("guardians", student.get("guardians") instanceof List<?> g ? g : List.of());
    out.put("summary", summary(student));

    List<Map<String, Object>> events = List.of();
    try {
      events = lifecycle.listEvents(id);
    } catch (Exception ignored) {
      // Lifecycle flag may be off — 360 still returns core profile.
    }
    out.put("lifecycleEvents", events);

    Map<String, Object> fee =
        admissionNo == null ? Map.of() : nullToEmpty(engines.getFeeClearance(scope, admissionNo));
    Map<String, Object> library =
        admissionNo == null
            ? Map.of()
            : nullToEmpty(engines.getLibraryClearance(scope, admissionNo));
    out.put("fee", fee);
    out.put("library", library);
    out.put("ops", snapshots.campusOps(scope, admissionNo));
    out.put("attendance", snapshots.recentAttendance(scope, id, admissionNo));
    out.put("exams", snapshots.publishedMarks(scope, id, admissionNo));
    out.put(
        "tabs",
        List.of(
            "profile",
            "guardians",
            "ops",
            "fees",
            "library",
            "attendance",
            "exams",
            "lifecycle"));
    return out;
  }

  private static Map<String, Object> summary(Map<String, Object> student) {
    Map<String, Object> answers = answers(student);
    Map<String, Object> s = new LinkedHashMap<>();
    s.put(
        "displayName",
        firstNonBlank(string(answers.get("fullName")), string(answers.get("studentName")), string(student.get("admissionNo"))));
    s.put("admissionNo", student.get("admissionNo"));
    s.put("status", student.get("status"));
    s.put(
        "classSection",
        firstNonBlank(string(answers.get("classSection")), string(answers.get("classApplied"))));
    s.put("mobile", answers.get("mobile"));
    s.put("email", answers.get("email"));
    s.put("house", answers.get("house"));
    s.put("branchId", student.get("branchId"));
    s.put("academicSessionId", student.get("academicSessionId"));
    return s;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> answers(Map<String, Object> student) {
    Object a = student.get("answers");
    return a instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
  }

  private static Map<String, Object> nullToEmpty(Map<String, Object> m) {
    return m == null ? Map.of() : m;
  }

  private static String string(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v)) {
        return v.trim();
      }
    }
    return null;
  }
}
