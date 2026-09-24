package com.sugamflow.school.academic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "academic")
public class AcademicProperties {

  private final Integrations integrations = new Integrations();

  public Integrations getIntegrations() {
    return integrations;
  }

  public static class Integrations {
    private String subscriptionBaseUrl = "http://localhost:8182";
    private String settingsBaseUrl = "http://localhost:8181";

    public String getSubscriptionBaseUrl() {
      return subscriptionBaseUrl;
    }

    public void setSubscriptionBaseUrl(String subscriptionBaseUrl) {
      this.subscriptionBaseUrl = subscriptionBaseUrl;
    }

    public String getSettingsBaseUrl() {
      return settingsBaseUrl;
    }

    public void setSettingsBaseUrl(String settingsBaseUrl) {
      this.settingsBaseUrl = settingsBaseUrl;
    }
  }
}
