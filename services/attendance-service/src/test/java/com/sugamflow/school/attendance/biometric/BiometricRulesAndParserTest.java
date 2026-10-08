package com.sugamflow.school.attendance.biometric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class BiometricRulesAndParserTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  @Test
  void parsesTabSeparatedAttlogAndSkipsShortLines() {
    String body = "\nOK\n10045\t2026-10-08 08:32:21\t0\t15\nbad line\n";
    List<ZkAttlogParser.Punch> punches = ZkAttlogParser.parse(body);
    assertEquals(1, punches.size());
    assertEquals("10045", punches.get(0).pin());
    assertEquals("FACE", ZkAttlogParser.verification(punches.get(0).verifyCode()));
    assertEquals("CHECK_IN", ZkAttlogParser.deviceStatusType(punches.get(0).statusCode()));
  }

  @Test
  void firstPunchIsInAndLateAfterGrace() {
    Instant punch = ZkAttlogParser.toInstant(
        java.time.LocalDateTime.of(2026, 10, 8, 8, 41), "Asia/Kolkata");
    BiometricRules.Decision decision =
        BiometricRules.decide(
            BiometricRules.FIRST_LAST,
            "BOTH",
            0,
            punch,
            null,
            null,
            LocalTime.of(8, 30),
            10,
            LocalTime.of(15, 0),
            LocalTime.NOON,
            240,
            ZONE);
    assertEquals("CHECK_IN", decision.eventType());
    assertEquals("LATE", decision.status());
    assertEquals(1, decision.lateMinutes());
  }

  @Test
  void earlyDepartureWhenOutBeforeSchoolEnd() {
    Instant in = ZkAttlogParser.toInstant(
        java.time.LocalDateTime.of(2026, 10, 8, 8, 25), "Asia/Kolkata");
    Instant out = ZkAttlogParser.toInstant(
        java.time.LocalDateTime.of(2026, 10, 8, 13, 45), "Asia/Kolkata");
    BiometricRules.Decision decision =
        BiometricRules.decide(
            BiometricRules.FIRST_LAST,
            "BOTH",
            0,
            out,
            in,
            null,
            LocalTime.of(8, 30),
            10,
            LocalTime.of(15, 0),
            LocalTime.NOON,
            240,
            ZONE);
    assertEquals("CHECK_OUT", decision.eventType());
    assertEquals("EARLY_DEPARTURE", decision.status());
    assertTrue(decision.earlyMinutes() > 0);
  }

  @Test
  void gateModeUsesDeviceDirection() {
    Instant punch = Instant.parse("2026-10-08T10:00:00Z");
    BiometricRules.Decision decision =
        BiometricRules.decide(
            BiometricRules.GATE,
            "OUT",
            0,
            punch,
            punch.minusSeconds(3600),
            null,
            LocalTime.of(8, 30),
            10,
            LocalTime.of(15, 0),
            LocalTime.NOON,
            0,
            ZONE);
    assertEquals("CHECK_OUT", decision.eventType());
  }

  @Test
  void eventHashIsStable() {
    String one = DeviceSecrets.eventHash("dev-1", "10045", "2026-10-08T03:02:21Z", "CHECK_IN");
    String two = DeviceSecrets.eventHash("dev-1", "10045", "2026-10-08T03:02:21Z", "CHECK_IN");
    String other = DeviceSecrets.eventHash("dev-2", "10045", "2026-10-08T03:02:21Z", "CHECK_IN");
    assertEquals(one, two);
    assertTrue(!one.equals(other));
  }
}
