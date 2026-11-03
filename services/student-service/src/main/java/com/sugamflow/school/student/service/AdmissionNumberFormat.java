package com.sugamflow.school.student.service;

import java.time.LocalDate;
import java.util.Map;

/** Builds an admission number from the school's saved format. Existing numbers are not rewritten. */
public final class AdmissionNumberFormat {

  private AdmissionNumberFormat() {}

  public static String format(
      Map<String, Object> settings, String branchId, long sequence, LocalDate today) {
    return stem(settings, branchId, today) + pad(sequence, width(settings));
  }

  public static String stem(Map<String, Object> settings, String branchId, LocalDate today) {
    String separator = separator(settings);
    StringBuilder stem = new StringBuilder(prefix(settings));
    String year = year(settings, today);
    if (!year.isEmpty()) {
      stem.append(separator).append(year);
    }
    if (includeBranch(settings)) {
      stem.append(separator).append(branch(branchId));
    }
    stem.append(separator);
    return stem.toString();
  }

  private static String prefix(Map<String, Object> settings) {
    String raw = text(settings == null ? null : settings.get("admissionNoPrefix"));
    String cleaned = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    if (cleaned.isBlank()) {
      return "ADM";
    }
    return cleaned.length() > 12 ? cleaned.substring(0, 12) : cleaned;
  }

  private static String separator(Map<String, Object> settings) {
    String raw = text(settings == null ? null : settings.get("admissionNoSeparator"));
    if (raw.isEmpty()) {
      return "-";
    }
    String trimmed = raw.trim();
    return trimmed.length() > 3 ? trimmed.substring(0, 3) : trimmed;
  }

  private static String year(Map<String, Object> settings, LocalDate today) {
    int digits = number(settings == null ? null : settings.get("admissionNoYearDigits"), 4);
    String year = String.valueOf(today.getYear());
    if (digits == 0) {
      return "";
    }
    if (digits == 2) {
      return year.substring(2);
    }
    return year;
  }

  private static boolean includeBranch(Map<String, Object> settings) {
    Object raw = settings == null ? null : settings.get("admissionNoIncludeBranch");
    if (raw == null) {
      return true;
    }
    if (raw instanceof Boolean flag) {
      return flag;
    }
    return !"false".equalsIgnoreCase(String.valueOf(raw).trim());
  }

  private static String branch(String branchId) {
    String branch =
        branchId == null || branchId.isBlank()
            ? "MAIN"
            : branchId.toUpperCase().replaceAll("[^A-Z0-9]", "");
    if (branch.isBlank()) {
      branch = "MAIN";
    }
    return branch.length() > 6 ? branch.substring(0, 6) : branch;
  }

  private static int width(Map<String, Object> settings) {
    int width = number(settings == null ? null : settings.get("admissionNoWidth"), 4);
    return Math.min(8, Math.max(1, width));
  }

  private static String pad(long sequence, int width) {
    long value = Math.max(1, sequence);
    return String.format("%0" + width + "d", value);
  }

  private static String text(Object raw) {
    return raw == null ? "" : String.valueOf(raw).trim();
  }

  private static int number(Object raw, int fallback) {
    if (raw instanceof Number n) {
      return n.intValue();
    }
    if (raw == null || String.valueOf(raw).isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(String.valueOf(raw).trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }
}
