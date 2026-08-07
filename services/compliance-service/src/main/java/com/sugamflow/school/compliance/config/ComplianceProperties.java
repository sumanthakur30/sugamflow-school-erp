package com.sugamflow.school.compliance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "compliance")
public class ComplianceProperties {
  private final Integrations integrations = new Integrations();
  private final Documents documents = new Documents();
  private final Exports exports = new Exports();
  private final Ai ai = new Ai();

  public Integrations getIntegrations() {
    return integrations;
  }

  public Documents getDocuments() {
    return documents;
  }

  public Exports getExports() {
    return exports;
  }

  public Ai getAi() {
    return ai;
  }

  public static class Integrations {
    private String subscriptionBaseUrl = "http://localhost:8182";
    private String studentBaseUrl = "http://localhost:8191";
    private String staffBaseUrl = "http://localhost:8198";
    private String cmsBaseUrl = "http://localhost:8201";

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

    public String getStaffBaseUrl() {
      return staffBaseUrl;
    }

    public void setStaffBaseUrl(String staffBaseUrl) {
      this.staffBaseUrl = staffBaseUrl;
    }

    public String getCmsBaseUrl() {
      return cmsBaseUrl;
    }

    public void setCmsBaseUrl(String cmsBaseUrl) {
      this.cmsBaseUrl = cmsBaseUrl;
    }
  }

  public static class Documents {
    private String storageDir = "./data/compliance-documents";
    private long maxFileBytes = 15_000_000L;
    private String allowedContentTypes =
        "application/pdf,image/jpeg,image/png,image/webp,application/msword,"
            + "application/vnd.openxmlformats-officedocument.wordprocessingml.document,"
            + "application/vnd.ms-excel,"
            + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private int expiryWarnDays = 60;

    public String getStorageDir() {
      return storageDir;
    }

    public void setStorageDir(String storageDir) {
      this.storageDir = storageDir;
    }

    public long getMaxFileBytes() {
      return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
      this.maxFileBytes = maxFileBytes;
    }

    public String getAllowedContentTypes() {
      return allowedContentTypes;
    }

    public void setAllowedContentTypes(String allowedContentTypes) {
      this.allowedContentTypes = allowedContentTypes;
    }

    public int getExpiryWarnDays() {
      return expiryWarnDays;
    }

    public void setExpiryWarnDays(int expiryWarnDays) {
      this.expiryWarnDays = expiryWarnDays;
    }
  }

  public static class Exports {
    private String storageDir = "./data/compliance-exports";

    public String getStorageDir() {
      return storageDir;
    }

    public void setStorageDir(String storageDir) {
      this.storageDir = storageDir;
    }
  }

  public static class Ai {
    private String apiUrl = "";
    private String apiKey = "";
    private String model = "gpt-4o-mini";

    public boolean isConfigured() {
      return apiUrl != null && !apiUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    public String getApiUrl() {
      return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
      this.apiUrl = apiUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }
}
