package com.sugamflow.school.student.lifecycle;

import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.web.StudentException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rule-engine clearance evaluation for TC issuance (fee dues, library books). */
public final class TcClearanceService {

  public static final String ACTION_BLOCK_TC = "BLOCK_TC";

  private TcClearanceService() {}

  public static Map<String, Object> evaluate(
      TenantScope scope,
      ConfigEngineClient engines,
      StudentRecordEntity student,
      Map<String, Object> tcPolicy,
      Map<String, Object> body) {
    boolean enabled = !Boolean.FALSE.equals(tcPolicy.get("clearanceEnabled"));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("clearanceEnabled", enabled);
    if (!enabled) {
      out.put("cleared", true);
      out.put("matchedActions", List.of());
      return out;
    }

    List<String> providers = stringList(tcPolicy.get("clearanceProviders"));
    if (providers.isEmpty()) {
      providers = List.of("fee", "library");
    }
    List<String> configuredBlockActions = stringList(tcPolicy.get("blockActions"));
    final List<String> blockActions =
        configuredBlockActions.isEmpty() ? List.of(ACTION_BLOCK_TC) : configuredBlockActions;

    @SuppressWarnings("unchecked")
    Map<String, Object> overrides =
        body.get("clearanceOverrides") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : Map.of();

    Map<String, Object> fees = Map.of();
    if (providers.contains("fee") && engines.isFeatureEnabled(scope, "FEATURE_FEE")) {
      fees =
          overrides.get("fees") instanceof Map<?, ?> fm
              ? new LinkedHashMap<>((Map<String, Object>) fm)
              : engines.getFeeClearance(scope, student.getAdmissionNo());
      if (fees == null) {
        fees = emptyFees(student.getAdmissionNo());
      }
    } else {
      fees = emptyFees(student.getAdmissionNo());
    }

    Map<String, Object> library = Map.of();
    if (providers.contains("library") && engines.isFeatureEnabled(scope, "FEATURE_LIBRARY")) {
      library =
          overrides.get("library") instanceof Map<?, ?> lm
              ? new LinkedHashMap<>((Map<String, Object>) lm)
              : engines.getLibraryClearance(scope, student.getAdmissionNo());
      if (library == null) {
        library = emptyLibrary(student.getAdmissionNo());
      }
    } else {
      library = emptyLibrary(student.getAdmissionNo());
    }

    Map<String, Object> studentCtx = new LinkedHashMap<>();
    studentCtx.put("id", student.getId().toString());
    studentCtx.put("admissionNo", student.getAdmissionNo());
    studentCtx.put("status", student.getStatus());
    studentCtx.put("fullName", student.getAnswers().get("fullName"));

    Map<String, Object> ruleContext = new LinkedHashMap<>();
    ruleContext.put("student", studentCtx);
    ruleContext.put("fees", fees);
    ruleContext.put("library", library);
    ruleContext.put(
        "tc",
        Map.of(
            "policyKey",
            stringOr(tcPolicy.get("definitionKey"), "default_tc"),
            "studentId",
            student.getId().toString()));

    List<String> matched = engines.evaluateRules(scope, ruleContext);
    boolean blocked =
        matched.stream().anyMatch(a -> blockActions.stream().anyMatch(b -> b.equalsIgnoreCase(a)));

    out.put("studentId", student.getId().toString());
    out.put("admissionNo", student.getAdmissionNo());
    out.put("fees", fees);
    out.put("library", library);
    out.put("matchedActions", matched);
    out.put("blockActions", blockActions);
    out.put("cleared", !blocked);
    if (blocked) {
      out.put("blockReason", buildBlockReason(fees, library, matched, blockActions));
    }
    return out;
  }

  public static void requireCleared(Map<String, Object> clearance) {
    if (Boolean.TRUE.equals(clearance.get("cleared"))) {
      return;
    }
    String reason = stringOr(clearance.get("blockReason"), "TC blocked by clearance rules");
    throw new StudentException(ACTION_BLOCK_TC, reason);
  }

  private static String buildBlockReason(
      Map<String, Object> fees,
      Map<String, Object> library,
      List<String> matched,
      List<String> blockActions) {
    List<String> parts = new ArrayList<>();
    if (Boolean.TRUE.equals(fees.get("hasDues"))) {
      parts.add("fee dues pending (" + fees.get("pendingAmount") + ")");
    }
    if (Boolean.TRUE.equals(library.get("hasOutstanding"))) {
      parts.add("library books outstanding (" + library.get("outstandingBooks") + ")");
    }
    if (parts.isEmpty()) {
      parts.add("matched actions: " + String.join(", ", matched));
    }
    return "TC clearance failed: "
        + String.join("; ", parts)
        + " ["
        + String.join(", ", blockActions)
        + "]";
  }

  private static Map<String, Object> emptyFees(String admissionNo) {
    return Map.of(
        "admissionNo", admissionNo,
        "pendingAmount", 0,
        "pendingDays", 0,
        "openCollections", 0,
        "hasDues", false);
  }

  private static Map<String, Object> emptyLibrary(String admissionNo) {
    return Map.of(
        "admissionNo", admissionNo,
        "outstandingBooks", 0,
        "maxOverdueDays", 0,
        "hasOutstanding", false);
  }

  @SuppressWarnings("unchecked")
  private static List<String> stringList(Object raw) {
    List<String> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item != null) {
          String s = String.valueOf(item).trim();
          if (!s.isEmpty()) {
            out.add(s);
          }
        }
      }
    }
    return out;
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? fallback : s;
  }
}
