package com.sugamflow.school.reportbuilder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "report")
public class ReportProperties {

  private final Integrations integrations = new Integrations();

  public Integrations getIntegrations() {
    return integrations;
  }

  public static class Integrations {
    private String subscriptionBaseUrl = "http://localhost:8182";
    private String studentBaseUrl = "http://localhost:8191";

    public String getSubscriptionBaseUrl() {
      return subscriptionBaseUrl;
    }

    public void setSubscriptionBaseUrl(String subscriptionBaseUrl) {
      this.subscriptionBaseUrl = subscriptionBaseUrl;
    }

    public String getStudentBaseUrl() {
      return studentBaseUrl;
    }

    public void setStudentBaseUrl(String studentBaseUrl) {
      this.studentBaseUrl = studentBaseUrl;
    }
  }
}
