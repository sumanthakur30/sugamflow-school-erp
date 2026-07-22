package com.sugamflow.school.settings.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.config.SettingsProperties;
import com.sugamflow.school.settings.integration.AuditClient;
import com.sugamflow.school.settings.integration.SubscriptionClient;
import com.sugamflow.school.settings.model.DesignTheme;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class SchoolProvisionerService {

  private static final Logger log = LoggerFactory.getLogger(SchoolProvisionerService.class);
  public static final String PLATFORM_DEFAULT_SCHOOL_NAME = "SugamFlow School";
  public static final String DEFAULT_PLAN_ID = "starter";
  private static final String BRANCH_MAIN = "main";

  private final BranchRegistryService branches;
  private final SettingsConfigService settings;
  private final SubscriptionClient subscription;
  private final AuditClient auditClient;
  private final SettingsProperties properties;
  private final RestClient.Builder restClientBuilder;

  public SchoolProvisionerService(
      BranchRegistryService branches,
      SettingsConfigService settings,
      SubscriptionClient subscription,
      AuditClient auditClient,
      SettingsProperties properties,
      RestClient.Builder restClientBuilder) {
    this.branches = branches;
    this.settings = settings;
    this.subscription = subscription;
    this.auditClient = auditClient;
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  /**
   * Idempotent first-login bootstrap for a newly registered school: main campus, Design Studio
   * branding (schoolName from shop when still platform default), starter plan, provisioned marker.
   */
  @Transactional
  public Map<String, Object> provision(Map<String, Object> request) {
    TenantScope scope = TenantContext.require();
    String org = scope.organizationId();
    String requestedName = firstNonBlank(asString(request != null ? request.get("schoolName") : null));

    Map<String, Object> branch = branches.ensureMainCampus(org);
    DesignTheme before = settings.getOrCreateTheme(org, BRANCH_MAIN);
    DesignTheme theme = settings.getOrCreateTheme(org, BRANCH_MAIN);

    Map<String, String> branding =
        theme.getBranding() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(theme.getBranding());
    String currentName = branding.get("schoolName");
    boolean alreadyProvisioned = branding.get("provisionedAt") != null && !branding.get("provisionedAt").isBlank();
    boolean nameIsDefault = isBlank(currentName) || PLATFORM_DEFAULT_SCHOOL_NAME.equals(currentName);

    String resolvedName = requestedName;
    if (isBlank(resolvedName) && nameIsDefault) {
      resolvedName = lookupShopName(org);
    }

    boolean changed = false;
    if (!isBlank(resolvedName) && nameIsDefault && !resolvedName.equals(currentName)) {
      branding.put("schoolName", resolvedName.trim());
      changed = true;
    }

    if (!alreadyProvisioned) {
      branding.put("provisionedAt", Instant.now().toString());
      changed = true;
    }

    if (changed) {
      theme.setBranding(branding);
      if (theme.getStatus() == null || theme.getStatus().isBlank() || "DRAFT".equals(theme.getStatus())) {
        theme.setStatus("PUBLISHED");
      }
      theme = settings.saveTheme(theme);
      auditClient.recordChange(
          "SCHOOL_PROVISION",
          org,
          before,
          theme,
          alreadyProvisioned ? "School provision refreshed branding" : "School provisioned");
    }

    Map<String, Object> plan = ensureStarterPlan(scope);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", org);
    out.put("provisioned", true);
    out.put("alreadyProvisioned", alreadyProvisioned && !changed);
    out.put("branch", branch);
    out.put("schoolName", theme.getBranding() != null ? theme.getBranding().get("schoolName") : null);
    out.put("themeStatus", theme.getStatus());
    out.put("planId", plan != null ? plan.get("planId") : DEFAULT_PLAN_ID);
    out.put("plan", plan);
    return out;
  }

  private Map<String, Object> ensureStarterPlan(TenantScope scope) {
    Map<String, Object> entitlements = subscription.getEntitlements(scope);
    if (entitlements != null && entitlements.get("planId") != null) {
      // Virtual default is also "starter"; always upsert so the tenant row exists.
      String planId = String.valueOf(entitlements.get("planId"));
      if (!DEFAULT_PLAN_ID.equals(planId)) {
        return entitlements;
      }
    }
    Map<String, Object> assigned = subscription.assignPlan(scope, DEFAULT_PLAN_ID);
    return assigned != null ? assigned : entitlements;
  }

  private String lookupShopName(String org) {
    String base = properties.getIntegrations().getPublicApiBaseUrl();
    if (base == null || base.isBlank()) {
      return null;
    }
    String url = base.replaceAll("/$", "") + "/api/v1/public/shops/" + org;
    try {
      Map<String, Object> body =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      if (body == null) {
        return null;
      }
      Object name = body.get("shopName");
      return name == null ? null : String.valueOf(name).trim();
    } catch (Exception ex) {
      log.info("Could not resolve shopName for {}: {}", org, ex.getMessage());
      return null;
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String asString(Object v) {
    return v == null ? null : String.valueOf(v).trim();
  }

  private static String firstNonBlank(String s) {
    return isBlank(s) ? null : s;
  }
}
