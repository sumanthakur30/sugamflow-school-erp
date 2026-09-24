package com.sugamflow.school.website.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SubscriptionEntitlementsClient {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionEntitlementsClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final String subscriptionBaseUrl;

  public SubscriptionEntitlementsClient(
      RestClient.Builder restClientBuilder,
      @Value("${website.integrations.subscription-base-url:http://localhost:8182}")
          String subscriptionBaseUrl) {
    this.restClientBuilder = restClientBuilder;
    this.subscriptionBaseUrl = subscriptionBaseUrl.replaceAll("/$", "");
  }

  public boolean isFeatureEnabled(String organizationId, String flag) {
    Map<String, Object> body =
        get(subscriptionBaseUrl + "/api/subscription/feature-flags/" + flag, organizationId);
    return body != null && Boolean.TRUE.equals(body.get("enabled"));
  }

  @SuppressWarnings("unchecked")
  public Map<String, Long> limits(String organizationId) {
    Map<String, Object> body =
        get(subscriptionBaseUrl + "/api/subscription/tenants/current/entitlements", organizationId);
    if (body == null || !(body.get("limits") instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Long> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> e : map.entrySet()) {
      if (e.getKey() == null || e.getValue() == null) continue;
      try {
        out.put(String.valueOf(e.getKey()), Long.valueOf(String.valueOf(e.getValue())));
      } catch (NumberFormatException ignored) {
      }
    }
    return out;
  }

  private Map<String, Object> get(String url, String organizationId) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(
                  h ->
                      TenantHeaders.apply(
                          h, new TenantScope(organizationId, null, null, null, null)))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope == null) return null;
      Object data = envelope.get("data");
      if (data instanceof Map<?, ?> map) {
        return (Map<String, Object>) map;
      }
      return envelope;
    } catch (RestClientResponseException ex) {
      log.warn("GET {} failed: {}", url, ex.getStatusCode());
      return null;
    } catch (Exception ex) {
      log.warn("GET {} failed: {}", url, ex.getMessage());
      return null;
    }
  }
}
