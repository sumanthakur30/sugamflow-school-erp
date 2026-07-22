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
    private String hostelBaseUrl = "http://localhost:8195";
    private String transportBaseUrl = "http://localhost:8196";
    private String staffBaseUrl = "http://localhost:8198";
    private String academicBaseUrl = "http://localhost:8199";
    private String attendanceBaseUrl = "http://localhost:8192";
    private String examBaseUrl = "http://localhost:8193";
    private String publicApiBaseUrl = "http://localhost:9090";
    private String publicUiBaseUrl = "http://localhost:4300";

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

    public String getHostelBaseUrl() {
      return hostelBaseUrl;
    }

    public void setHostelBaseUrl(String hostelBaseUrl) {
      this.hostelBaseUrl = hostelBaseUrl;
    }

    public String getTransportBaseUrl() {
      return transportBaseUrl;
    }

    public void setTransportBaseUrl(String transportBaseUrl) {
      this.transportBaseUrl = transportBaseUrl;
    }

    public String getStaffBaseUrl() {
      return staffBaseUrl;
    }

    public void setStaffBaseUrl(String staffBaseUrl) {
      this.staffBaseUrl = staffBaseUrl;
    }

    public String getAcademicBaseUrl() {
      return academicBaseUrl;
    }

    public void setAcademicBaseUrl(String academicBaseUrl) {
      this.academicBaseUrl = academicBaseUrl;
    }

    public String getAttendanceBaseUrl() {
      return attendanceBaseUrl;
    }

    public void setAttendanceBaseUrl(String attendanceBaseUrl) {
      this.attendanceBaseUrl = attendanceBaseUrl;
    }

    public String getExamBaseUrl() {
      return examBaseUrl;
    }

    public void setExamBaseUrl(String examBaseUrl) {
      this.examBaseUrl = examBaseUrl;
    }

    public String getPublicApiBaseUrl() {
      return publicApiBaseUrl;
    }

    public void setPublicApiBaseUrl(String publicApiBaseUrl) {
      this.publicApiBaseUrl = publicApiBaseUrl;
    }

    public String getPublicUiBaseUrl() {
      return publicUiBaseUrl;
    }

    public void setPublicUiBaseUrl(String publicUiBaseUrl) {
      this.publicUiBaseUrl = publicUiBaseUrl;
    }
  }
}
