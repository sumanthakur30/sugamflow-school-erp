package com.sugamflow.school.fee.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/** Checks for one student discount before it is stored. The calculator still caps what is billed. */
public final class StudentDiscountRules {

  private StudentDiscountRules() {}

  public static String validate(String mode, BigDecimal value, String reason, LocalDate from, LocalDate to) {
    if ("PERCENT".equals(mode)) {
      if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
        return "Percentage must be greater than 0 and no more than 100.";
      }
    } else if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
      return "FIXED_OFF".equals(mode)
          ? "Fixed rupee discount must be greater than 0."
          : "Discount value must be greater than 0.";
    }
    if (reason == null || reason.trim().length() < 3) {
      return "A reason is required for a student fee adjustment";
    }
    String trimmed = reason.trim();
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if ("other".equals(lower) || lower.startsWith("other:")) {
      String custom = lower.startsWith("other:") ? trimmed.substring(trimmed.indexOf(':') + 1).trim() : "";
      if (custom.length() < 3) {
        return "Enter a custom reason.";
      }
    }
    if (from != null && to != null && to.isBefore(from)) {
      return "Valid Until cannot be earlier than Valid From.";
    }
    return null;
  }

  /**
   * A fixed discount cannot exceed a known eligible fee or a positive outstanding balance.
   * A missing amount, or an outstanding balance of zero, is not treated as a fee of zero.
   */
  public static String fixedCap(String mode, BigDecimal value, BigDecimal eligible, BigDecimal pending) {
    if (!"FIXED_OFF".equals(mode) || value == null) {
      return null;
    }
    if (eligible != null && eligible.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(eligible) > 0) {
      return "Fixed discount cannot exceed the eligible fee.";
    }
    if (pending != null && pending.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(pending) > 0) {
      return "Fixed discount cannot exceed the outstanding balance.";
    }
    return null;
  }
}
