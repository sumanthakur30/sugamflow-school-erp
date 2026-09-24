package com.sugamflow.school.cms.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Reads platform subscription entitlements (limits) — does not own plan logic. */
@Component
public class SubscriptionEntitlementsClient {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionEntitlementsClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final String subscriptionBaseUrl;

  public SubscriptionEntitlementsClient(
      RestClient.Builder restClientBuilder,
      @Value("${cms.integrations.subscription-base-url:http://localhost:8182}")
          String subscriptionBaseUrl) {
    this.restClientBuilder = restClientBuilder;
    this.subscriptionBaseUrl = subscriptionBaseUrl.replaceAll("/$", "");
  }

  @SuppressWarnings("unchecked")
  public Map<String, Long> limits(String organizationId) {
    Map<String, Object> body =
        get(subscriptionBaseUrl + "/api/subscription/tenants/current/entitlements", organizationId);
    if (body == null) {
      return Map.of();
    }
    Object limits = body.get("limits");
    if (!(limits instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Long> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> e : map.entrySet()) {
      if (e.getKey() == null || e.getValue() == null) {
        continue;
      }
      try {
        out.put(String.valueOf(e.getKey()), Long.valueOf(String.valueOf(e.getValue())));
      } catch (NumberFormatException ignored) {
        // skip non-numeric
      }
    }
    return out;
  }

  public boolean isFeatureEnabled(String organizationId, String flag) {
    Map<String, Object> body =
        get(subscriptionBaseUrl + "/api/subscription/feature-flags/" + flag, organizationId);
    return body != null && Boolean.TRUE.equals(body.get("enabled"));
  }

  /** Best-effort usage metering; never blocks the primary write path on meter failure. */
  public void incrementUsage(String organizationId, String limitCode, long delta, String reason) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("limitCode", limitCode);
      payload.put("delta", delta);
      payload.put("reason", reason);
      restClientBuilder
          .build()
          .post()
          .uri(subscriptionBaseUrl + "/api/subscription/tenants/current/increment-usage")
          .contentType(MediaType.APPLICATION_JSON)
          .headers(h -> TenantHeaders.apply(h, scope(organizationId)))
          .body(payload)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception ex) {
      log.warn("increment-usage {} failed for {}: {}", limitCode, organizationId, ex.getMessage());
    }
  }

  private Map<String, Object> get(String url, String organizationId) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope(organizationId)))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope == null) {
        return null;
      }
      Object data = envelope.get("data");
      if (data instanceof Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        return cast;
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

  private static TenantScope scope(String organizationId) {
    return new TenantScope(organizationId, null, null, null, null);
  }
}
