package com.sugamflow.school.fee.finance;

import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Checks a fee structure before a new version is stored. Issued demands are not rewritten. */
public final class FeeStructureRules {

  private static final Set<String> FREQUENCIES =
      Set.of(
          FinanceCatalog.FREQ_MONTHLY,
          FinanceCatalog.FREQ_QUARTERLY,
          FinanceCatalog.FREQ_HALF_YEARLY,
          FinanceCatalog.FREQ_YEARLY,
          FinanceCatalog.FREQ_ONE_TIME);

  private FeeStructureRules() {}

  public static void validate(Map<String, Object> payload) {
    Object raw = payload == null ? null : payload.get("lines");
    if (!(raw instanceof List<?> lines) || lines.isEmpty()) {
      throw new FeeException("VALIDATION", "Add at least one fee head");
    }
    Set<String> seen = new LinkedHashSet<>();
    boolean active = false;
    for (Object item : lines) {
      if (!(item instanceof Map<?, ?> line)) {
        throw new FeeException("VALIDATION", "Each fee line must include a fee head, amount, and frequency");
      }
      String head = text(line.get("headKey")).toUpperCase(Locale.ROOT);
      if (head.isBlank()) {
        throw new FeeException("VALIDATION", "Each fee line needs a fee head");
      }
      if (!seen.add(head)) {
        throw new FeeException("VALIDATION", "Duplicate fee head " + head);
      }
      String frequency = text(line.get("frequency")).toUpperCase(Locale.ROOT);
      if (frequency.isBlank()) {
        frequency = FinanceCatalog.FREQ_MONTHLY;
      }
      if (!FREQUENCIES.contains(frequency)) {
        throw new FeeException("VALIDATION", "Frequency for " + head + " is not supported");
      }
      BigDecimal amount = amount(line.get("amount"));
      if (amount.compareTo(BigDecimal.ZERO) < 0) {
        throw new FeeException("VALIDATION", "Amount for " + head + " cannot be negative");
      }
      if (isActive(line)) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
          throw new FeeException("VALIDATION", "Active fee head " + head + " needs an amount greater than zero");
        }
        active = true;
      }
    }
    if (!active) {
      throw new FeeException("VALIDATION", "Add at least one active fee head");
    }
  }

  private static boolean isActive(Map<?, ?> line) {
    Object active = line.get("active");
    if (Boolean.FALSE.equals(active) || "false".equalsIgnoreCase(text(active))) {
      return false;
    }
    return !"INACTIVE".equalsIgnoreCase(text(line.get("status")));
  }

  private static BigDecimal amount(Object raw) {
    if (raw == null || String.valueOf(raw).isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(String.valueOf(raw).trim());
    } catch (NumberFormatException ex) {
      throw new FeeException("VALIDATION", "Fee amount must be a number");
    }
  }

  private static String text(Object raw) {
    return raw == null ? "" : String.valueOf(raw).trim();
  }
}
