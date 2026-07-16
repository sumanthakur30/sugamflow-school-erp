package com.sugamflow.school.settings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "settings")
public class SettingsProperties {

  private final Integrations integrations = new Integrations();

  public Integrations getIntegrations() {
    return integrations;
  }

  public static class Integrations {
    private String auditBaseUrl = "http://localhost:8188";
    private String subscriptionBaseUrl = "http://localhost:8182";
    private String publicApiBaseUrl = "http://localhost:9090";

    public String getAuditBaseUrl() {
      return auditBaseUrl;
    }

    public void setAuditBaseUrl(String auditBaseUrl) {
      this.auditBaseUrl = auditBaseUrl;
    }

    public String getSubscriptionBaseUrl() {
      return subscriptionBaseUrl;
    }

    public void setSubscriptionBaseUrl(String subscriptionBaseUrl) {
      this.subscriptionBaseUrl = subscriptionBaseUrl;
    }

    public String getPublicApiBaseUrl() {
      return publicApiBaseUrl;
    }

    public void setPublicApiBaseUrl(String publicApiBaseUrl) {
      this.publicApiBaseUrl = publicApiBaseUrl;
    }
  }
}

