package com.sugamflow.school.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 2 dual-read for entitlements. Default {@code json} preserves today's behaviour
 * (plan JSON only). See docs/PLATFORM-SUBSCRIPTION-PHASE2.md.
 */
@ConfigurationProperties(prefix = "subscription.entitlements")
public class SubscriptionEntitlementsProperties {

  /**
   * {@code json} — JSON only (default). {@code dual} — JSON base, fill gaps from plan_* projection.
   * {@code projection} — prefer projection when it has feature rows; else JSON.
   */
  private String readMode = "json";

  public String getReadMode() {
    return readMode;
  }

  public void setReadMode(String readMode) {
    this.readMode = readMode;
  }

  public ReadMode resolvedMode() {
    if (readMode == null || readMode.isBlank()) {
      return ReadMode.JSON;
    }
    return switch (readMode.trim().toLowerCase()) {
      case "dual" -> ReadMode.DUAL;
      case "projection" -> ReadMode.PROJECTION;
      default -> ReadMode.JSON;
    };
  }

  public enum ReadMode {
    JSON,
    DUAL,
    PROJECTION
  }
}
