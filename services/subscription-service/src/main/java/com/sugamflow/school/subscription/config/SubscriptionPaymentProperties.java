package com.sugamflow.school.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "subscription.payment")
public class SubscriptionPaymentProperties {

  /** simulate | razorpay | auto (razorpay when keys present). */
  private String mode = "auto";

  private final Razorpay razorpay = new Razorpay();

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public Razorpay getRazorpay() {
    return razorpay;
  }

  public static class Razorpay {
    private String keyId = "";
    private String keySecret = "";
    private String webhookSecret = "";

    public String getKeyId() {
      return keyId;
    }

    public void setKeyId(String keyId) {
      this.keyId = keyId;
    }

    public String getKeySecret() {
      return keySecret;
    }

    public void setKeySecret(String keySecret) {
      this.keySecret = keySecret;
    }

    public String getWebhookSecret() {
      return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
      this.webhookSecret = webhookSecret;
    }
  }
}
