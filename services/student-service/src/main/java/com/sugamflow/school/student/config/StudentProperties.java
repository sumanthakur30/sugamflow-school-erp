package com.sugamflow.school.student.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "student")
public class StudentProperties {

  private final Defaults defaults = new Defaults();
  private final Integrations integrations = new Integrations();

  public Defaults getDefaults() {
    return defaults;
  }

  public Integrations getIntegrations() {
    return integrations;
  }

  public static class Defaults {
    private String formKey = "student_master";

    public String getFormKey() {
      return formKey;
    }

    public void setFormKey(String formKey) {
      this.formKey = formKey;
    }
  }

  public static class Integrations {
    private String formsBaseUrl = "http://localhost:8183";
    private String subscriptionBaseUrl = "http://localhost:8182";
    private String settingsBaseUrl = "http://localhost:8181";
    private String reportsBaseUrl = "http://localhost:8186";
    private String rulesBaseUrl = "http://localhost:8185";
    private String feeBaseUrl = "http://localhost:8190";
    private String libraryBaseUrl = "http://localhost:8194";

    public String getFormsBaseUrl() {
      return formsBaseUrl;
    }

    public void setFormsBaseUrl(String formsBaseUrl) {
      this.formsBaseUrl = formsBaseUrl;
    }

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

    public String getReportsBaseUrl() {
      return reportsBaseUrl;
    }

    public void setReportsBaseUrl(String reportsBaseUrl) {
      this.reportsBaseUrl = reportsBaseUrl;
    }

    public String getRulesBaseUrl() {
      return rulesBaseUrl;
    }

    public void setRulesBaseUrl(String rulesBaseUrl) {
      this.rulesBaseUrl = rulesBaseUrl;
    }

    public String getFeeBaseUrl() {
      return feeBaseUrl;
    }

    public void setFeeBaseUrl(String feeBaseUrl) {
      this.feeBaseUrl = feeBaseUrl;
    }

    public String getLibraryBaseUrl() {
      return libraryBaseUrl;
    }

    public void setLibraryBaseUrl(String libraryBaseUrl) {
      this.libraryBaseUrl = libraryBaseUrl;
    }
  }
}
