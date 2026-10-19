package com.sugamflow.school.student.service;

import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Student and visitor gate-pass transitions. Status is never taken from the client. */
public final class GatePassRules {

  public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  public static final Set<String> ELEVATED =
      Set.of("SHOP_OWNER", "SUPER_ADMIN", "ADMIN", "PRINCIPAL", "OWNER");

  public static final Set<String> VISITOR_CATEGORIES =
      Set.of("PARENT", "VENDOR", "GUEST", "OFFICIAL", "OTHER");

  private static final Set<String> OPEN_STUDENT = Set.of("PENDING", "APPROVED", "OPEN", "OUT");

  private GatePassRules() {}

  public static boolean elevated(String role) {
    return role != null && ELEVATED.contains(role.trim().toUpperCase(Locale.ROOT));
  }

  public static String passType(Map<String, Object> payload, String subjectType) {
    String fromPayload = text(payload == null ? null : payload.get("passType"));
    if ("STUDENT".equals(fromPayload) || "VISITOR".equals(fromPayload)) {
      return fromPayload;
    }
    return "VISITOR".equalsIgnoreCase(text(subjectType)) ? "VISITOR" : "STUDENT";
  }

  public static String createStatus(String passType, boolean requestApproval, boolean canApprove) {
    if ("VISITOR".equals(passType)) {
      return "INSIDE";
    }
    if (requestApproval && !canApprove) {
      return "PENDING";
    }
    return "APPROVED";
  }

  /**
   * @param action APPROVE, REJECT, EXIT, RETURN, CHECK_OUT, CANCEL
   * @return the next status
   */
  public static String nextStatus(String passType, String current, String action, boolean canApprove) {
    String from = normalize(current);
    String step = normalize(action);
    if ("STUDENT".equals(passType)) {
      return studentNext(from, step, canApprove);
    }
    return visitorNext(from, step);
  }

  public static boolean openStudent(String status) {
    return OPEN_STUDENT.contains(normalize(status));
  }

  public static boolean matches(Map<String, Object> row, Map<String, String> query) {
    if (query == null || query.isEmpty()) {
      return true;
    }
    Map<String, Object> payload = payload(row.get("payload"));
    String type = passType(payload, text(row.get("subjectType")));
    String wantedType = upper(query.get("passType"));
    if (!wantedType.isEmpty() && !wantedType.equals(type)) {
      return false;
    }
    String status = upper(query.get("status"));
    if (!status.isEmpty() && !status.equals(normalize(text(row.get("status"))))) {
      return false;
    }
    String className = lower(query.get("className"));
    String rowClass = lower(text(payload.get("className")));
    if (!className.isEmpty() && !rowClass.contains(className)) {
      return false;
    }
    String section = lower(query.get("section"));
    if (!section.isEmpty() && !section.equals(lower(text(payload.get("section"))))) {
      return false;
    }
    String purpose = lower(query.get("purpose"));
    if (!purpose.isEmpty() && !lower(text(payload.get("purpose"))).contains(purpose)) {
      return false;
    }
    String passDate = text(payload.get("passDate"));
    String from = text(query.get("from"));
    String to = text(query.get("to"));
    if (!from.isEmpty() && (passDate.isEmpty() || passDate.compareTo(from) < 0)) {
      return false;
    }
    if (!to.isEmpty() && (passDate.isEmpty() || passDate.compareTo(to) > 0)) {
      return false;
    }
    String q = lower(query.get("q"));
    if (q.isEmpty()) {
      return true;
    }
    String haystack =
        String.join(
            " ",
            lower(text(row.get("title"))),
            lower(text(row.get("subjectName"))),
            lower(text(row.get("subjectRef"))),
            lower(text(payload.get("admissionNo"))),
            lower(text(payload.get("visitorName"))),
            lower(text(payload.get("mobile"))),
            lower(text(payload.get("passNumber"))),
            lower(text(payload.get("personToMeet"))));
    return haystack.contains(q);
  }

  public static LocalDate today(Instant now) {
    return now.atZone(ZONE).toLocalDate();
  }

  private static String studentNext(String from, String action, boolean canApprove) {
    return switch (action) {
      case "APPROVE" -> {
        require(canApprove, "Approval is limited to the principal or school admin");
        require("PENDING".equals(from), "Only a pending pass can be approved");
        yield "APPROVED";
      }
      case "REJECT" -> {
        require(canApprove, "Rejection is limited to the principal or school admin");
        require("PENDING".equals(from), "Only a pending pass can be rejected");
        yield "REJECTED";
      }
      case "EXIT" -> {
        require("APPROVED".equals(from) || "OPEN".equals(from), "Record exit only after the pass is approved");
        yield "OUT";
      }
      case "RETURN" -> {
        require("OUT".equals(from), "Record return only after the student has exited");
        yield "RETURNED";
      }
      case "CANCEL" -> {
        require(
            "PENDING".equals(from) || "APPROVED".equals(from) || "OPEN".equals(from),
            "This pass can no longer be cancelled");
        yield "CANCELLED";
      }
      default -> throw new StudentException("VALIDATION", "Unsupported gate pass action");
    };
  }

  private static String visitorNext(String from, String action) {
    return switch (action) {
      case "CHECK_OUT" -> {
        require("INSIDE".equals(from) || "OPEN".equals(from), "Check-out is only for a visitor who is inside");
        yield "COMPLETED";
      }
      case "CANCEL" -> {
        require("INSIDE".equals(from) || "OPEN".equals(from), "This visit can no longer be cancelled");
        yield "CANCELLED";
      }
      case "APPROVE", "REJECT", "EXIT", "RETURN" ->
          throw new StudentException("VALIDATION", "That action does not apply to a visitor pass");
      default -> throw new StudentException("VALIDATION", "Unsupported gate pass action");
    };
  }

  private static void require(boolean ok, String message) {
    if (!ok) {
      throw new StudentException("VALIDATION", message);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> payload(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private static String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String lower(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
