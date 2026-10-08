package com.sugamflow.school.attendance.biometric;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Defensive ZKTeco ATTLOG parser. Firmware layouts differ, so short lines are skipped. */
public final class ZkAttlogParser {

  private static final DateTimeFormatter ZK_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private ZkAttlogParser() {}

  public record Punch(String pin, LocalDateTime deviceTime, int statusCode, int verifyCode) {}

  public static List<Punch> parse(String body) {
    List<Punch> punches = new ArrayList<>();
    if (body == null || body.isBlank()) {
      return punches;
    }
    for (String raw : body.split("\\r?\\n")) {
      String line = raw.trim();
      if (line.isEmpty() || line.startsWith("OK") || line.startsWith("#")) {
        continue;
      }
      String[] parts = line.split("\\t");
      if (parts.length < 2) {
        parts = line.split("\\s+", 4);
      }
      if (parts.length < 2) {
        continue;
      }
      String pin = parts[0].trim();
      if (pin.isEmpty()) {
        continue;
      }
      LocalDateTime time = parseTime(parts[1].trim());
      if (time == null) {
        continue;
      }
      int status = parts.length > 2 ? parseInt(parts[2], 0) : 0;
      int verify = parts.length > 3 ? parseInt(parts[3], 1) : 1;
      punches.add(new Punch(pin, time, status, verify));
    }
    return punches;
  }

  public static String verification(int code) {
    return switch (code) {
      case 0, 3 -> "PIN";
      case 2, 4 -> "RFID";
      case 15 -> "FACE";
      case 20, 21 -> "FACE_FINGERPRINT";
      default -> "FINGERPRINT";
    };
  }

  public static String deviceStatusType(int code) {
    return switch (code) {
      case 1, 2, 5 -> "CHECK_OUT";
      default -> "CHECK_IN";
    };
  }

  public static java.time.Instant toInstant(LocalDateTime deviceTime, String zoneId) {
    ZoneId zone;
    try {
      zone = ZoneId.of(zoneId == null || zoneId.isBlank() ? "Asia/Kolkata" : zoneId);
    } catch (Exception ex) {
      zone = ZoneId.of("Asia/Kolkata");
    }
    return deviceTime.atZone(zone).toInstant();
  }

  private static LocalDateTime parseTime(String raw) {
    String value = raw.replace('T', ' ');
    if (value.length() >= 19) {
      value = value.substring(0, 19);
    }
    try {
      return LocalDateTime.parse(value, ZK_TIME);
    } catch (DateTimeParseException ex) {
      try {
        return LocalDateTime.parse(value.toUpperCase(Locale.ROOT), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      } catch (DateTimeParseException ignored) {
        return null;
      }
    }
  }

  private static int parseInt(String raw, int fallback) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }
}
