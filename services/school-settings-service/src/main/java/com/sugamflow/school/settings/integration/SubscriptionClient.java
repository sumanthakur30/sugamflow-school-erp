package com.sugamflow.school.settings.integration;

import com.sugamflow.school.common.cache.TtlCache;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.settings.config.SettingsProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SubscriptionClient {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final SettingsProperties properties;

  public SubscriptionClient(RestClient.Builder restClientBuilder, SettingsProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  private final TtlCache<String, Boolean> featureFlagCache = TtlCache.forConfig();
  private final TtlCache<String, Map<String, Object>> entitlementsCache = TtlCache.forConfig();

  public boolean isFeatureEnabled(TenantScope scope, String flag) {
    String cacheKey = scope.organizationId() + "|" + flag;
    Boolean enabled =
        featureFlagCache.get(
            cacheKey,
            key -> {
              Map<String, Object> body =
                  get(
                      properties.getIntegrations().getSubscriptionBaseUrl()
                          + "/api/subscription/feature-flags/"
                          + flag,
                      scope);
              return body == null ? null : Boolean.TRUE.equals(body.get("enabled"));
            });
    return Boolean.TRUE.equals(enabled);
  }

  /** Returns maxBranches from entitlements; -1 = unlimited; default 1 if unavailable. */
  public long getMaxBranches(TenantScope scope) {
    Map<String, Object> body = getEntitlements(scope);
    if (body == null) {
      return 1L;
    }
    Object limitsObj = body.get("limits");
    if (!(limitsObj instanceof Map<?, ?> limits)) {
      return 1L;
    }
    Object raw = limits.get("maxBranches");
    if (raw == null) {
      return 1L;
    }
    try {
      return Long.parseLong(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return 1L;
    }
  }

  public Map<String, Object> getEntitlements(TenantScope scope) {
    return entitlementsCache.get(
        scope.organizationId(),
        key ->
            get(
                properties.getIntegrations().getSubscriptionBaseUrl()
                    + "/api/subscription/tenants/current/entitlements",
                scope));
  }

  /**
   * Best-effort assign of a plan for new schools. Safe to call repeatedly; subscription-service
   * upserts the tenant row.
   */
  public Map<String, Object> assignPlan(TenantScope scope, String planId) {
    String url =
        properties.getIntegrations().getSubscriptionBaseUrl()
            + "/api/subscription/tenants/current/plan/"
            + planId;
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .put()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      // Plan changed: drop cached flags/entitlements so the new plan applies immediately.
      featureFlagCache.clear();
      entitlementsCache.invalidate(scope.organizationId());
      return unwrap(envelope);
    } catch (RestClientResponseException ex) {
      log.warn("PUT {} failed: {}", url, ex.getStatusCode());
      return null;
    } catch (Exception ex) {
      log.warn("PUT {} failed: {}", url, ex.getMessage());
      return null;
    }
  }

  private Map<String, Object> get(String url, TenantScope scope) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(
                  h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      return unwrap(envelope);
    } catch (RestClientResponseException ex) {
      log.warn("GET {} failed: {}", url, ex.getStatusCode());
      return null;
    } catch (Exception ex) {
      log.warn("GET {} failed: {}", url, ex.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> unwrap(Map<String, Object> envelope) {
    if (envelope == null) {
      return null;
    }
    Object data = envelope.get("data");
    if (data instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return envelope;
  }
}
