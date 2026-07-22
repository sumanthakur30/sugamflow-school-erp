package com.sugamflow.school.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school.notification")
public class NotificationConfigProperties {

  private final Integrations integrations = new Integrations();

  public Integrations getIntegrations() {
    return integrations;
  }

  public static class Integrations {
    private String studentBaseUrl = "http://localhost:8191";
    private String notificationDeliveryBaseUrl = "http://localhost:8087";

    public String getStudentBaseUrl() {
      return studentBaseUrl;
    }

    public void setStudentBaseUrl(String studentBaseUrl) {
      this.studentBaseUrl = studentBaseUrl;
    }

    public String getNotificationDeliveryBaseUrl() {
      return notificationDeliveryBaseUrl;
    }

    public void setNotificationDeliveryBaseUrl(String notificationDeliveryBaseUrl) {
      this.notificationDeliveryBaseUrl = notificationDeliveryBaseUrl;
    }
  }
}
