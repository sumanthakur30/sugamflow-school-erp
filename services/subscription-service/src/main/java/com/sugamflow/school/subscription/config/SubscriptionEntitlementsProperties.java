package com.sugamflow.school.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 2 dual-read for entitlements. Default {@code json} preserves today's behaviour
 * (plan JSON only). See docs/PLATFORM-SUBSCRIPTION-PHASE2.md.
 *
 * <p>Phase 2.4: after smoke, set {@code read-mode=dual} then {@code projection}, and/or
 * {@code prefer-projection=true} so projection wins on key conflicts in dual mode.
 */
@ConfigurationProperties(prefix = "subscription.entitlements")
public class SubscriptionEntitlementsProperties {

  /**
   * {@code json} — JSON only (default). {@code dual} — JSON base, fill gaps from plan_* projection.
   * {@code projection} — prefer projection when it has feature rows; else JSON.
   */
  private String readMode = "json";

  /**
   * When true and {@code read-mode=dual}, projection values overwrite JSON for the same feature/limit
   * keys (Phase 2.4 soft prefer). Ignored for {@code json} / {@code projection} modes.
   */
  private boolean preferProjection = false;

  public String getReadMode() {
    return readMode;
  }

  public void setReadMode(String readMode) {
    this.readMode = readMode;
  }

  public boolean isPreferProjection() {
    return preferProjection;
  }

  public void setPreferProjection(boolean preferProjection) {
    this.preferProjection = preferProjection;
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
