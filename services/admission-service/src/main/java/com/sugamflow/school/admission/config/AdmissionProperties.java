package com.sugamflow.school.admission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admission")
public class AdmissionProperties {

  private final Defaults defaults = new Defaults();
  private final Integrations integrations = new Integrations();
  private final PublicCaptcha publicCaptcha = new PublicCaptcha();

  public Defaults getDefaults() {
    return defaults;
  }

  public Integrations getIntegrations() {
    return integrations;
  }

  public PublicCaptcha getPublicCaptcha() {
    return publicCaptcha;
  }

  public static class PublicCaptcha {
    /** When true, public apply requires captchaToken (reCAPTCHA when secret set). */
    private boolean enabled = false;
    private String secret = "";
    private String verifyUrl = "https://www.google.com/recaptcha/api/siteverify";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public String getVerifyUrl() {
      return verifyUrl;
    }

    public void setVerifyUrl(String verifyUrl) {
      this.verifyUrl = verifyUrl;
    }
  }

  public static class Defaults {
    private String formKey = "admission_form";
    private String workflowKey = "admission";
    private String offerLetterTemplateKey = "offer_letter";

    public String getFormKey() {
      return formKey;
    }

    public void setFormKey(String formKey) {
      this.formKey = formKey;
    }

    public String getWorkflowKey() {
      return workflowKey;
    }

    public void setWorkflowKey(String workflowKey) {
      this.workflowKey = workflowKey;
    }

    public String getOfferLetterTemplateKey() {
      return offerLetterTemplateKey;
    }

    public void setOfferLetterTemplateKey(String offerLetterTemplateKey) {
      this.offerLetterTemplateKey = offerLetterTemplateKey;
    }
  }

  public static class Integrations {
    private String formsBaseUrl = "http://localhost:8183";
    private String workflowsBaseUrl = "http://localhost:8184";
    private String rulesBaseUrl = "http://localhost:8185";
    private String subscriptionBaseUrl = "http://localhost:8182";
    private String settingsBaseUrl = "http://localhost:8181";
    private String notificationConfigBaseUrl = "http://localhost:8187";
    private String reportsBaseUrl = "http://localhost:8186";
    private String notificationDeliveryBaseUrl = "http://localhost:8087";
    /** Public gateway base used in offer-letter download links inside emails. */
    private String publicApiBaseUrl = "http://localhost:9090";
    private String studentBaseUrl = "http://localhost:8191";

    public String getFormsBaseUrl() {
      return formsBaseUrl;
    }

    public void setFormsBaseUrl(String formsBaseUrl) {
      this.formsBaseUrl = formsBaseUrl;
    }

    public String getWorkflowsBaseUrl() {
      return workflowsBaseUrl;
    }

    public void setWorkflowsBaseUrl(String workflowsBaseUrl) {
      this.workflowsBaseUrl = workflowsBaseUrl;
    }

    public String getRulesBaseUrl() {
      return rulesBaseUrl;
    }

    public void setRulesBaseUrl(String rulesBaseUrl) {
      this.rulesBaseUrl = rulesBaseUrl;
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

    public String getNotificationConfigBaseUrl() {
      return notificationConfigBaseUrl;
    }

    public void setNotificationConfigBaseUrl(String notificationConfigBaseUrl) {
      this.notificationConfigBaseUrl = notificationConfigBaseUrl;
    }

    public String getReportsBaseUrl() {
      return reportsBaseUrl;
    }

    public void setReportsBaseUrl(String reportsBaseUrl) {
      this.reportsBaseUrl = reportsBaseUrl;
    }

    public String getNotificationDeliveryBaseUrl() {
      return notificationDeliveryBaseUrl;
    }

    public void setNotificationDeliveryBaseUrl(String notificationDeliveryBaseUrl) {
      this.notificationDeliveryBaseUrl = notificationDeliveryBaseUrl;
    }

    public String getPublicApiBaseUrl() {
      return publicApiBaseUrl;
    }

    public void setPublicApiBaseUrl(String publicApiBaseUrl) {
      this.publicApiBaseUrl = publicApiBaseUrl;
    }

    public String getStudentBaseUrl() {
      return studentBaseUrl;
    }

    public void setStudentBaseUrl(String studentBaseUrl) {
      this.studentBaseUrl = studentBaseUrl;
    }
  }
}
