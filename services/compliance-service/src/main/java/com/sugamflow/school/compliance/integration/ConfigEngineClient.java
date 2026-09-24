package com.sugamflow.school.compliance.integration;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.sugamflow.school.common.cache.TtlCache;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.config.ComplianceProperties;

@Component
public class ConfigEngineClient {
  private static final Logger log = LoggerFactory.getLogger(ConfigEngineClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final ComplianceProperties properties;
  private final TtlCache<String, Boolean> featureFlagCache = TtlCache.forConfig();

  public ConfigEngineClient(RestClient.Builder restClientBuilder, ComplianceProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

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

  private Map<String, Object> get(String url, TenantScope scope) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
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
    if (data == null) {
      return envelope;
    }
    Map<String, Object> wrap = new LinkedHashMap<>();
    wrap.put("value", data);
    return wrap;
  }
}
