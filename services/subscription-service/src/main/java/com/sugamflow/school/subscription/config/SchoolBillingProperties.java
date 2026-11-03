package com.sugamflow.school.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "subscription.school-billing")
public class SchoolBillingProperties {

  /** Scheduled monthly run. Manual billing stays available when this is false. */
  private boolean enabled = false;

  private String studentBaseUrl = "http://localhost:8191";

  private String cron = "0 30 2 1 * *";

  private String zone = "Asia/Kolkata";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getStudentBaseUrl() {
    return studentBaseUrl;
  }

  public void setStudentBaseUrl(String studentBaseUrl) {
    this.studentBaseUrl = studentBaseUrl;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String getZone() {
    return zone;
  }

  public void setZone(String zone) {
    this.zone = zone;
  }
}
