package com.sugamflow.school.admission.lead;

import com.sugamflow.school.admission.web.AdmissionException;
import java.util.Locale;

public enum LeadStatus {
  INTERESTED,
  NOT_INTERESTED,
  ADMIN,
  FOLLOW_UP,
  CALL_BACK,
  BLACKLISTED,
  ADMISSION_CREATED;

  public String label() {
    return switch (this) {
      case INTERESTED -> "Interested";
      case NOT_INTERESTED -> "Not Interested";
      case ADMIN -> "Admin";
      case FOLLOW_UP -> "Follow-up";
      case CALL_BACK -> "Call back";
      case BLACKLISTED -> "Blacklisted";
      case ADMISSION_CREATED -> "Admission Created";
    };
  }

  public static LeadStatus parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return INTERESTED;
    }
    String key =
        raw.trim()
            .toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_')
            .replace("/", "_");
    key =
        switch (key) {
          case "NOTINTERESTED", "NOT_INTRESTED" -> "NOT_INTERESTED";
          case "CALLBACK", "CALL" -> "CALL_BACK";
          case "FOLLOWUP" -> "FOLLOW_UP";
          case "DROPPED", "BLACKLIST" -> "BLACKLISTED";
          case "ADMISSION", "ADMISSION_CREATE", "ADMITTED" -> "ADMISSION_CREATED";
          default -> key;
        };
    try {
      return LeadStatus.valueOf(key);
    } catch (IllegalArgumentException ex) {
      throw new AdmissionException("VALIDATION", "Unknown lead status: " + raw);
    }
  }
}
