package com.sugamflow.school.attendance.biometric;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Device secrets are stored as SHA-256. The plaintext key is returned only when generated. */
public final class DeviceSecrets {

  private static final SecureRandom RANDOM = new SecureRandom();

  private DeviceSecrets() {}

  public static String newKey() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String key) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  public static String prefix(String key) {
    if (key == null || key.length() < 6) {
      return "••••";
    }
    return key.substring(0, 6);
  }

  public static boolean matches(String key, String hash) {
    if (key == null || key.isBlank() || hash == null || hash.isBlank()) {
      return false;
    }
    return hash(key).equals(hash);
  }

  public static String eventHash(String deviceId, String personCode, String eventTime, String eventType) {
    return hash(deviceId + "|" + personCode + "|" + eventTime + "|" + eventType);
  }
}
