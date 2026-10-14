package com.sugamflow.school.fee.finance;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Decides which fee-head frequencies belong on one monthly bill. */
public final class FeePeriodFilter {

  private FeePeriodFilter() {}

  public static YearMonth monthOf(String periodKey) {
    if (periodKey == null || periodKey.isBlank()) {
      return null;
    }
    String value = periodKey.trim();
    try {
      if (value.matches("\\d{4}-\\d{2}")) {
        return YearMonth.parse(value);
      }
      if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
        return YearMonth.from(LocalDate.parse(value));
      }
    } catch (DateTimeParseException ex) {
      return null;
    }
    return null;
  }

  public static boolean include(String frequency, int month, int sessionStartMonth, boolean includeOneTime) {
    String kind = normalize(frequency);
    int start = sessionStartMonth < 1 || sessionStartMonth > 12 ? 4 : sessionStartMonth;
    int offset = Math.floorMod(month - start, 12);
    return switch (kind) {
      case "M" -> true;
      case "Q" -> offset % 3 == 0;
      case "HY" -> offset % 6 == 0;
      case "Y" -> offset == 0;
      case "OT" -> includeOneTime;
      default -> true;
    };
  }

  public static String demandKey(String organizationId, String studentRef, String structureKey, String periodKey) {
    return "DEMAND|" + organizationId + "|" + studentRef + "|" + structureKey + "|" + periodKey;
  }

  public static boolean covers(LocalDate from, LocalDate to, LocalDate asOf) {
    if (asOf == null) {
      return true;
    }
    if (from != null && asOf.isBefore(from)) {
      return false;
    }
    return to == null || !asOf.isAfter(to);
  }

  public static String normalizePeriod(String raw) {
    YearMonth month = monthOf(raw);
    if (month != null) {
      return month.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
    if (raw == null) {
      return "";
    }
    String value = raw.trim();
    String[] parts = value.split("\\s+");
    if (parts.length == 2) {
      Integer namedMonth = monthNumber(parts[0]);
      if (namedMonth != null && parts[1].matches("\\d{4}")) {
        return parts[1] + "-" + String.format("%02d", namedMonth);
      }
    }
    return value.toUpperCase(Locale.ROOT);
  }

  public static boolean samePeriod(String stored, String requested) {
    return normalizePeriod(stored).equals(normalizePeriod(requested));
  }

  private static String normalize(String frequency) {
    if (frequency == null || frequency.isBlank()) {
      return "OT";
    }
    String value = frequency.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    return switch (value) {
      case "MONTHLY", "M" -> "M";
      case "QUARTERLY", "Q" -> "Q";
      case "HALF_YEARLY", "HALFYEARLY", "HY" -> "HY";
      case "YEARLY", "ANNUAL", "Y" -> "Y";
      case "ONE_TIME", "ONETIME", "OT" -> "OT";
      default -> value;
    };
  }

  private static Integer monthNumber(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "january", "jan" -> 1;
      case "february", "feb" -> 2;
      case "march", "mar" -> 3;
      case "april", "apr" -> 4;
      case "may" -> 5;
      case "june", "jun" -> 6;
      case "july", "jul" -> 7;
      case "august", "aug" -> 8;
      case "september", "sep", "sept" -> 9;
      case "october", "oct" -> 10;
      case "november", "nov" -> 11;
      case "december", "dec" -> 12;
      default -> null;
    };
  }
}
