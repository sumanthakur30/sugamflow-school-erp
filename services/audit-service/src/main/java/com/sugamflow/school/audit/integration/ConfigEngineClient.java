package com.sugamflow.school.audit.integration;

import com.sugamflow.school.audit.config.AuditProperties;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.tenant.TenantHeaders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ConfigEngineClient {

  private static final Logger log = LoggerFactory.getLogger(ConfigEngineClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final AuditProperties properties;

  public ConfigEngineClient(RestClient.Builder restClientBuilder, AuditProperties properties) {
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

  public void restoreDesignTheme(TenantScope scope, Map<String, Object> theme) {
    put(
        properties.getIntegrations().getSettingsBaseUrl() + "/api/config/design-studio/theme",
        scope,
        theme != null ? theme : Map.of());
  }

  public void restoreModuleSettings(TenantScope scope, String moduleKey, Map<String, Object> body) {
    put(
        properties.getIntegrations().getSettingsBaseUrl() + "/api/config/modules/" + moduleKey,
        scope,
        body != null ? body : Map.of());
  }

  public void restoreLocalization(TenantScope scope, Map<String, Object> body) {
    put(
        properties.getIntegrations().getSettingsBaseUrl() + "/api/config/localization",
        scope,
        body != null ? body : Map.of());
  }

  public void restoreMenus(TenantScope scope, Object menus) {
    Object payload = menus;
    if (menus instanceof Map<?, ?> map && map.containsKey("nodes")) {
      payload = map.get("nodes");
    } else if (menus instanceof Map<?, ?> map && map.containsKey("value")) {
      payload = map.get("value");
    }
    put(
        properties.getIntegrations().getSettingsBaseUrl() + "/api/config/menus",
        scope,
        payload != null ? payload : List.of());
  }

  private Map<String, Object> get(String url, TenantScope scope) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> applyTenant(h, scope))
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

  private void put(String url, TenantScope scope, Object body) {
    try {
      restClientBuilder
          .build()
          .put()
          .uri(url)
          .contentType(MediaType.APPLICATION_JSON)
          .headers(
              h -> {
                applyTenant(h, scope);
                // Avoid re-auditing restores (would loop / noise).
                h.set("X-Skip-Config-Audit", "true");
              })
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException ex) {
      log.warn("PUT {} failed: {}", url, ex.getStatusCode());
      throw new IllegalStateException("Failed to apply rollback to settings: " + ex.getStatusCode());
    } catch (Exception ex) {
      log.warn("PUT {} failed: {}", url, ex.getMessage());
      throw new IllegalStateException("Failed to apply rollback to settings: " + ex.getMessage());
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
    if (data == null) {
      return envelope;
    }
    Map<String, Object> wrap = new LinkedHashMap<>();
    wrap.put("value", data);
    return wrap;
  }

  private static void applyTenant(
      org.springframework.http.HttpHeaders headers, TenantScope scope) {
    TenantHeaders.apply(headers, scope);
  }
}
