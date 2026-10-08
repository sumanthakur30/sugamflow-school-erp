package com.sugamflow.school.attendance.biometric;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * School-owned attendance rules. The device only supplies a punch; this class decides
 * IN/OUT, late, early departure, and half day.
 */
public final class BiometricRules {

  public static final String FIRST_LAST = "FIRST_LAST";
  public static final String DEVICE_STATUS = "DEVICE_STATUS";
  public static final String GATE = "GATE";
  public static final String TIME_SPLIT = "TIME_SPLIT";

  private BiometricRules() {}

  public record Decision(
      String eventType,
      String status,
      int lateMinutes,
      int earlyMinutes,
      int workingMinutes,
      Instant firstIn,
      Instant lastOut) {}

  public static String eventType(
      String mode, String deviceDirection, int deviceStatusCode, Instant punch, LocalTime split, ZoneId zone) {
    String selected = mode == null ? FIRST_LAST : mode;
    return switch (selected) {
      case DEVICE_STATUS -> ZkAttlogParser.deviceStatusType(deviceStatusCode);
      case GATE -> "OUT".equalsIgnoreCase(deviceDirection) ? "CHECK_OUT" : "CHECK_IN";
      case TIME_SPLIT -> {
        LocalTime local = ZonedDateTime.ofInstant(punch, zone).toLocalTime();
        yield local.isBefore(split == null ? LocalTime.NOON : split) ? "CHECK_IN" : "CHECK_OUT";
      }
      default -> null;
    };
  }

  public static Decision decide(
      String mode,
      String deviceDirection,
      int deviceStatusCode,
      Instant punch,
      Instant existingIn,
      Instant existingOut,
      LocalTime schoolStart,
      int graceMinutes,
      LocalTime schoolEnd,
      LocalTime split,
      int halfDayMinutes,
      ZoneId zone) {
    return decide(
        mode,
        deviceDirection,
        deviceStatusCode,
        punch,
        existingIn,
        existingOut,
        schoolStart,
        graceMinutes,
        schoolEnd,
        split,
        halfDayMinutes,
        zone,
        null);
  }

  public static Decision decide(
      String mode,
      String deviceDirection,
      int deviceStatusCode,
      Instant punch,
      Instant existingIn,
      Instant existingOut,
      LocalTime schoolStart,
      int graceMinutes,
      LocalTime schoolEnd,
      LocalTime split,
      int halfDayMinutes,
      ZoneId zone,
      String preferredType) {
    Instant first = existingIn;
    Instant last = existingOut;
    String typed =
        preferredType != null
            ? preferredType
            : eventType(mode, deviceDirection, deviceStatusCode, punch, split, zone);
    if (typed == null) {
      if (first == null || !punch.isAfter(first)) {
        first = punch;
        typed = "CHECK_IN";
      } else {
        last = punch;
        typed = "CHECK_OUT";
      }
    } else if ("CHECK_IN".equals(typed)) {
      if (first == null || punch.isBefore(first)) {
        first = punch;
      }
    } else if (last == null || punch.isAfter(last)) {
      last = punch;
    }
    if (first == null) {
      first = punch;
    }
    int late = lateMinutes(first, schoolStart, graceMinutes, zone);
    int early = 0;
    int working = 0;
    String status = late > 0 ? "LATE" : "PRESENT";
    if (last != null && last.isAfter(first)) {
      working = (int) Duration.between(first, last).toMinutes();
      early = earlyMinutes(last, schoolEnd, zone);
      if (halfDayMinutes > 0 && working < halfDayMinutes) {
        status = "HALF_DAY";
      } else if (early > 0 && late == 0) {
        status = "EARLY_DEPARTURE";
      } else if (early > 0 && late > 0) {
        status = "LATE";
      }
    }
    return new Decision(typed, status, late, early, working, first, last);
  }

  public static int lateMinutes(Instant firstIn, LocalTime start, int grace, ZoneId zone) {
    if (firstIn == null || start == null) {
      return 0;
    }
    ZonedDateTime local = firstIn.atZone(zone);
    ZonedDateTime cutoff = local.toLocalDate().atTime(start).plusMinutes(Math.max(0, grace)).atZone(zone);
    if (!local.isAfter(cutoff)) {
      return 0;
    }
    return (int) Duration.between(cutoff, local).toMinutes();
  }

  public static int earlyMinutes(Instant lastOut, LocalTime end, ZoneId zone) {
    if (lastOut == null || end == null) {
      return 0;
    }
    ZonedDateTime local = lastOut.atZone(zone);
    ZonedDateTime scheduled = local.toLocalDate().atTime(end).atZone(zone);
    if (!local.isBefore(scheduled)) {
      return 0;
    }
    return (int) Duration.between(local, scheduled).toMinutes();
  }

  public static String rosterStatus(String biometricStatus) {
    if (biometricStatus == null) {
      return "PRESENT";
    }
    return switch (biometricStatus) {
      case "LATE" -> "LATE";
      case "HALF_DAY" -> "HALF_DAY";
      case "LEAVE" -> "LEAVE";
      case "ABSENT" -> "ABSENT";
      default -> "PRESENT";
    };
  }
}
