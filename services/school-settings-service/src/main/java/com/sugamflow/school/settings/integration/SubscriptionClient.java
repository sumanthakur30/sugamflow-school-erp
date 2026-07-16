package com.sugamflow.school.settings.integration;

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

  public boolean isFeatureEnabled(TenantScope scope, String flag) {
    Map<String, Object> body =
        get(
            properties.getIntegrations().getSubscriptionBaseUrl()
                + "/api/subscription/feature-flags/"
                + flag,
            scope);
    return body != null && Boolean.TRUE.equals(body.get("enabled"));
  }

  /** Returns maxBranches from entitlements; -1 = unlimited; default 1 if unavailable. */
  public long getMaxBranches(TenantScope scope) {
    Map<String, Object> body =
        get(
            properties.getIntegrations().getSubscriptionBaseUrl()
                + "/api/subscription/tenants/current/entitlements",
            scope);
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
