package com.sugamflow.school.subscription.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fully configuration-driven plan. No hardcoded entitlements in domain code —
 * callers must resolve Feature Flags and limits from this model.
 */
public class SubscriptionPlan {

  private String id;
  private String code;
  private String name;
  private String planType;
  private boolean active = true;
  private Map<String, Long> limits = new LinkedHashMap<>();
  private Map<String, Boolean> featureFlags = new LinkedHashMap<>();

  public static SubscriptionPlan starter() {
    SubscriptionPlan p = base("starter", "Starter", "STARTER");
    p.limits.put("maxStudents", 200L);
    p.limits.put("maxTeachers", 20L);
    p.limits.put("maxBranches", 3L);
    p.limits.put("maxUsers", 30L);
    p.limits.put("maxStorageGb", 5L);
    p.limits.put("maxApiCalls", 10000L);
    p.limits.put("maxSms", 500L);
    p.limits.put("maxWhatsApp", 200L);
    p.limits.put("maxEmails", 1000L);
    p.limits.put("maxReports", 20L);
    p.limits.put("maxCustomFields", 10L);
    p.limits.put("maxSubjects", 30L);
    p.limits.put("maxSections", 40L);
    p.limits.put("maxClasses", 20L);
    p.limits.put("maxSessions", 2L);
    p.limits.put("aiUsageUnits", 0L);
    applyCommonFlags(p, false);
    p.featureFlags.put("FEATURE_LIBRARY", true);
    p.featureFlags.put("FEATURE_HOSTEL", true);
    p.featureFlags.put("FEATURE_TRANSPORT", true);
    p.featureFlags.put("FEATURE_PAYROLL", true);
    p.featureFlags.put("FEATURE_STAFF_MASTER", true);
    p.featureFlags.put("FEATURE_PARENT_APP", true);
    p.featureFlags.put("FEATURE_TEACHER_APP", true);
    p.featureFlags.put("FEATURE_MULTI_BRANCH", true);
    p.featureFlags.put("FEATURE_AI", true);
    p.featureFlags.put("FEATURE_BIOMETRIC", true);
    p.featureFlags.put("FEATURE_FACE_RECOGNITION", true);
    p.featureFlags.put("FEATURE_GPS", true);
    p.featureFlags.put("FEATURE_OFFLINE_MODE", true);
    p.featureFlags.put("FEATURE_ADMISSION", true);
    p.featureFlags.put("FEATURE_FEE", true);
    p.featureFlags.put("FEATURE_STUDENT_MASTER", true);
    p.featureFlags.put("FEATURE_ACADEMIC_LIFECYCLE", true);
    p.featureFlags.put("FEATURE_OPS_DEPTH", true);
    p.featureFlags.put("FEATURE_ATTENDANCE", true);
    p.featureFlags.put("FEATURE_EXAM", true);
    return p;
  }

  public static SubscriptionPlan enterprise() {
    SubscriptionPlan p = base("enterprise", "Enterprise", "ENTERPRISE");
    p.limits.replaceAll((k, v) -> -1L); // -1 = unlimited
    applyCommonFlags(p, true);
    return p;
  }

  /** Standalone CRM Starter — no School ERP features. */
  public static SubscriptionPlan crmStarter() {
    return crmPlan(
        "crm-starter",
        "CRM Starter",
        "CRM_STARTER",
        Map.ofEntries(
            Map.entry("crm.max_users", 5L),
            Map.entry("crm.max_pipelines", 1L),
            Map.entry("crm.max_leads", 2000L),
            Map.entry("crm.max_storage_mb", 1024L),
            Map.entry("crm.max_api_calls_month", 5000L),
            Map.entry("crm.ai_calls_month", 0L),
            Map.entry("crm.max_whatsapp_month", 200L),
            Map.entry("maxUsers", 5L),
            Map.entry("maxStorageGb", 1L),
            Map.entry("maxApiCalls", 5000L),
            Map.entry("maxWhatsApp", 200L),
            Map.entry("maxSms", 200L),
            Map.entry("maxEmails", 1000L)),
        Map.ofEntries(
            Map.entry("FEATURE_CRM", true),
            Map.entry("FEATURE_CRM_LEADS", true),
            Map.entry("FEATURE_CRM_PIPELINE", true),
            Map.entry("FEATURE_CRM_ACTIVITIES", true),
            Map.entry("FEATURE_CRM_IMPORT", true),
            Map.entry("FEATURE_CRM_API", false),
            Map.entry("FEATURE_CRM_QUOTE", false),
            Map.entry("FEATURE_CRM_APPROVAL", false),
            Map.entry("FEATURE_CRM_AUTOMATION", false),
            Map.entry("FEATURE_CRM_SEQUENCES", false),
            Map.entry("FEATURE_CRM_CAMPAIGN", false),
            Map.entry("FEATURE_CRM_AI", false),
            Map.entry("FEATURE_CRM_WHATSAPP", false),
            Map.entry("FEATURE_CRM_SMS", false),
            Map.entry("FEATURE_CRM_EMAIL", true),
            Map.entry("FEATURE_CRM_CASES", false)));
  }

  /** Standalone CRM Professional. */
  public static SubscriptionPlan crmProfessional() {
    return crmPlan(
        "crm-professional",
        "CRM Professional",
        "CRM_PROFESSIONAL",
        Map.ofEntries(
            Map.entry("crm.max_users", 25L),
            Map.entry("crm.max_pipelines", 10L),
            Map.entry("crm.max_leads", 25000L),
            Map.entry("crm.max_storage_mb", 10240L),
            Map.entry("crm.max_api_calls_month", 100000L),
            Map.entry("crm.ai_calls_month", 0L),
            Map.entry("crm.max_whatsapp_month", 5000L),
            Map.entry("maxUsers", 25L),
            Map.entry("maxStorageGb", 10L),
            Map.entry("maxApiCalls", 100000L),
            Map.entry("maxWhatsApp", 5000L),
            Map.entry("maxSms", 5000L),
            Map.entry("maxEmails", 20000L)),
        Map.ofEntries(
            Map.entry("FEATURE_CRM", true),
            Map.entry("FEATURE_CRM_LEADS", true),
            Map.entry("FEATURE_CRM_PIPELINE", true),
            Map.entry("FEATURE_CRM_ACTIVITIES", true),
            Map.entry("FEATURE_CRM_IMPORT", true),
            Map.entry("FEATURE_CRM_API", true),
            Map.entry("FEATURE_CRM_QUOTE", true),
            Map.entry("FEATURE_CRM_APPROVAL", true),
            Map.entry("FEATURE_CRM_AUTOMATION", true),
            Map.entry("FEATURE_CRM_SEQUENCES", true),
            Map.entry("FEATURE_CRM_CAMPAIGN", false),
            Map.entry("FEATURE_CRM_AI", false),
            Map.entry("FEATURE_CRM_WHATSAPP", true),
            Map.entry("FEATURE_CRM_SMS", true),
            Map.entry("FEATURE_CRM_EMAIL", true),
            Map.entry("FEATURE_CRM_CASES", false)));
  }

  /** Standalone CRM Enterprise. */
  public static SubscriptionPlan crmEnterprise() {
    return crmPlan(
        "crm-enterprise",
        "CRM Enterprise",
        "CRM_ENTERPRISE",
        Map.ofEntries(
            Map.entry("crm.max_users", -1L),
            Map.entry("crm.max_pipelines", -1L),
            Map.entry("crm.max_leads", -1L),
            Map.entry("crm.max_storage_mb", -1L),
            Map.entry("crm.max_api_calls_month", -1L),
            Map.entry("crm.ai_calls_month", 5000L),
            Map.entry("crm.max_whatsapp_month", -1L),
            Map.entry("maxUsers", -1L),
            Map.entry("maxStorageGb", -1L),
            Map.entry("maxApiCalls", -1L),
            Map.entry("maxWhatsApp", -1L),
            Map.entry("maxSms", -1L),
            Map.entry("maxEmails", -1L)),
        Map.ofEntries(
            Map.entry("FEATURE_CRM", true),
            Map.entry("FEATURE_CRM_LEADS", true),
            Map.entry("FEATURE_CRM_PIPELINE", true),
            Map.entry("FEATURE_CRM_ACTIVITIES", true),
            Map.entry("FEATURE_CRM_IMPORT", true),
            Map.entry("FEATURE_CRM_API", true),
            Map.entry("FEATURE_CRM_QUOTE", true),
            Map.entry("FEATURE_CRM_APPROVAL", true),
            Map.entry("FEATURE_CRM_AUTOMATION", true),
            Map.entry("FEATURE_CRM_SEQUENCES", true),
            Map.entry("FEATURE_CRM_CAMPAIGN", true),
            Map.entry("FEATURE_CRM_AI", true),
            Map.entry("FEATURE_CRM_WHATSAPP", true),
            Map.entry("FEATURE_CRM_SMS", true),
            Map.entry("FEATURE_CRM_EMAIL", true),
            Map.entry("FEATURE_CRM_CASES", true)));
  }

  /** Phase 2.3 — hospital / poly / pharmacy / pathlab / retail standalone plans (no School FEATURE_*). */
  public static SubscriptionPlan hospitalStarter() {
    return verticalPlan(
        "hospital-starter",
        "Hospital Starter",
        "HOSPITAL_STARTER",
        Map.of("maxUsers", 25L, "maxBranches", 1L, "maxStorageGb", 10L, "maxApiCalls", 50000L),
        Map.of(
            "HOSPITAL_OPD", true,
            "HOSPITAL_BILLING", true,
            "HOSPITAL_LAB", false,
            "HOSPITAL_PHARMACY", false,
            "HOSPITAL_IPD", false));
  }

  public static SubscriptionPlan hospitalPro() {
    return verticalPlan(
        "hospital-pro",
        "Hospital Professional",
        "HOSPITAL_PRO",
        Map.of("maxUsers", 100L, "maxBranches", 5L, "maxStorageGb", 50L, "maxApiCalls", 200000L),
        Map.of(
            "HOSPITAL_OPD", true,
            "HOSPITAL_IPD", true,
            "HOSPITAL_BILLING", true,
            "HOSPITAL_LAB", true,
            "HOSPITAL_PHARMACY", true));
  }

  public static SubscriptionPlan polyStarter() {
    return verticalPlan(
        "poly-starter",
        "Polyclinic Starter",
        "POLY_STARTER",
        Map.of("maxUsers", 30L, "maxBranches", 2L, "maxStorageGb", 15L, "maxApiCalls", 80000L),
        Map.of("POLY_OPD", true, "POLY_PHARMACY", true, "POLY_LAB", true, "DOCTOR_LAB_REPORTS", true));
  }

  public static SubscriptionPlan pharmacyStarter() {
    return verticalPlan(
        "pharmacy-starter",
        "Pharmacy Starter",
        "PHARMACY_STARTER",
        Map.of("maxUsers", 15L, "maxBranches", 2L, "maxStorageGb", 10L, "maxApiCalls", 50000L),
        Map.of(
            "PHARMACY_SALES", true,
            "PHARMACY_PURCHASE", true,
            "PHARMACY_STOCK", true,
            "PHARMACY_PO", true));
  }

  public static SubscriptionPlan pathlabStarter() {
    return verticalPlan(
        "pathlab-starter",
        "PathLab Starter",
        "PATHLAB_STARTER",
        Map.of("maxUsers", 20L, "maxBranches", 2L, "maxStorageGb", 20L, "maxApiCalls", 80000L),
        Map.of(
            "PATHLAB_REGISTRATION", true,
            "PATHLAB_SAMPLE", true,
            "PATHLAB_RESULTS", true,
            "PATHLAB_BILLING", true,
            "PATHLAB_BARCODE", true));
  }

  public static SubscriptionPlan retailStarter() {
    return verticalPlan(
        "retail-starter",
        "Retail Starter",
        "RETAIL_STARTER",
        Map.of("maxUsers", 20L, "maxBranches", 3L, "maxStorageGb", 10L, "maxApiCalls", 50000L),
        Map.of(
            "RETAIL_POS", true,
            "RETAIL_INVENTORY", true,
            "RETAIL_PURCHASE", true,
            "RETAIL_CUSTOMERS", true,
            "RETAIL_REPORTS", true));
  }

  private static SubscriptionPlan verticalPlan(
      String id,
      String name,
      String type,
      Map<String, Long> limits,
      Map<String, Boolean> flags) {
    return crmPlan(id, name, type, limits, flags);
  }

  private static SubscriptionPlan crmPlan(
      String id,
      String name,
      String type,
      Map<String, Long> limits,
      Map<String, Boolean> flags) {
    SubscriptionPlan p = new SubscriptionPlan();
    p.id = id;
    p.code = id.replace('-', '_').toUpperCase();
    p.name = name;
    p.planType = type;
    p.limits = new LinkedHashMap<>(limits);
    p.featureFlags = new LinkedHashMap<>(flags);
    return p;
  }

  private static SubscriptionPlan base(String id, String name, String type) {
    SubscriptionPlan p = new SubscriptionPlan();
    p.id = id;
    p.code = id.toUpperCase();
    p.name = name;
    p.planType = type;
    p.limits.putAll(
        Map.ofEntries(
            Map.entry("maxStudents", 1000L),
            Map.entry("maxTeachers", 100L),
            Map.entry("maxBranches", 3L),
            Map.entry("maxUsers", 150L),
            Map.entry("maxStorageGb", 50L),
            Map.entry("maxApiCalls", 100000L),
            Map.entry("maxSms", 5000L),
            Map.entry("maxWhatsApp", 5000L),
            Map.entry("maxEmails", 20000L),
            Map.entry("maxReports", 200L),
            Map.entry("maxCustomFields", 100L),
            Map.entry("maxSubjects", 100L),
            Map.entry("maxSections", 200L),
            Map.entry("maxClasses", 80L),
            Map.entry("maxSessions", 5L),
            Map.entry("aiUsageUnits", 1000L)));
    return p;
  }

  private static void applyCommonFlags(SubscriptionPlan p, boolean allOn) {
    String[] flags = {
      "FEATURE_DOCUMENT_STORAGE",
      "FEATURE_CLOUD_BACKUP",
      "FEATURE_BIOMETRIC",
      "FEATURE_GPS",
      "FEATURE_FACE_RECOGNITION",
      "FEATURE_LIBRARY",
      "FEATURE_HOSTEL",
      "FEATURE_TRANSPORT",
      "FEATURE_PAYROLL",
      "FEATURE_ACCOUNTING",
      "FEATURE_VISITOR",
      "FEATURE_HR",
      "FEATURE_INVENTORY",
      "FEATURE_API_ACCESS",
      "FEATURE_WHITE_LABEL",
      "FEATURE_CUSTOM_DOMAIN",
      "FEATURE_CUSTOM_BRANDING",
      "FEATURE_WEBSITE",
      "FEATURE_WEBSITE_CMS",
      "FEATURE_WEBSITE_ADMISSION",
      "FEATURE_WEBSITE_SEO",
      "FEATURE_WEBSITE_BLOG",
      "FEATURE_AUDIT_LOGS",
      "FEATURE_ROLE_LIMITS",
      "FEATURE_APPROVAL_WORKFLOW",
      "FEATURE_DIGITAL_SIGNATURE",
      "FEATURE_MULTI_PAYMENT_GATEWAY",
      "FEATURE_PARENT_APP",
      "FEATURE_TEACHER_APP",
      "FEATURE_STUDENT_APP",
      "FEATURE_MULTI_BRANCH",
      "FEATURE_OFFLINE_MODE",
      "FEATURE_ADMIN_CONFIG",
      "FEATURE_FORM_BUILDER",
      "FEATURE_WORKFLOW_BUILDER",
      "FEATURE_RULE_ENGINE",
      "FEATURE_REPORT_BUILDER",
      "FEATURE_ADMISSION",
      "FEATURE_FEE",
      "FEATURE_STUDENT_MASTER",
      "FEATURE_STAFF_MASTER",
      "FEATURE_ACADEMIC_LIFECYCLE",
      "FEATURE_OPS_DEPTH",
      "FEATURE_ATTENDANCE",
      "FEATURE_EXAM",
      "FEATURE_AI"
    };
    for (String f : flags) {
      p.featureFlags.put(f, allOn);
    }
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPlanType() {
    return planType;
  }

  public void setPlanType(String planType) {
    this.planType = planType;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Map<String, Long> getLimits() {
    return limits;
  }

  public void setLimits(Map<String, Long> limits) {
    this.limits = limits;
  }

  public Map<String, Boolean> getFeatureFlags() {
    return featureFlags;
  }

  public void setFeatureFlags(Map<String, Boolean> featureFlags) {
    this.featureFlags = featureFlags;
  }
}
