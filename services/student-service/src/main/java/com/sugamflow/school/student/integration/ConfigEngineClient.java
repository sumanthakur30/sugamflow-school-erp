package com.sugamflow.school.student.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.cache.TtlCache;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.config.StudentProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ConfigEngineClient {

  private static final Logger log = LoggerFactory.getLogger(ConfigEngineClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final StudentProperties properties;

  public ConfigEngineClient(RestClient.Builder restClientBuilder, StudentProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  private final TtlCache<String, Boolean> featureFlagCache = TtlCache.forConfig();
  private final TtlCache<String, Map<String, Object>> moduleSettingsCache = TtlCache.forConfig();

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

  public Map<String, Object> getModuleSettings(TenantScope scope, String moduleKey) {
    String cacheKey =
        scope.organizationId()
            + "|"
            + scope.branchId()
            + "|"
            + scope.academicSessionId()
            + "|"
            + moduleKey;
    Map<String, Object> body =
        moduleSettingsCache.get(
            cacheKey,
            key ->
                get(
                    properties.getIntegrations().getSettingsBaseUrl()
                        + "/api/config/modules/"
                        + moduleKey,
                    scope));
    return body != null ? body : Map.of();
  }

  public Map<String, Object> getForm(TenantScope scope, String formKey) {
    return get(properties.getIntegrations().getFormsBaseUrl() + "/api/forms/" + formKey, scope);
  }

  public Map<String, Object> renderReport(
      TenantScope scope, String templateKey, Map<String, Object> data) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("format", "PDF");
    payload.put("data", data != null ? data : Map.of());
    Map<String, Object> result =
        post(
            properties.getIntegrations().getReportsBaseUrl()
                + "/api/reports/templates/"
                + templateKey
                + "/render",
            scope,
            payload);
    return result != null ? result : Map.of();
  }

  public List<String> evaluateRules(TenantScope scope, Map<String, Object> context) {
    Map<String, Object> wrapped =
        post(properties.getIntegrations().getRulesBaseUrl() + "/api/rules/evaluate", scope, context);
    if (wrapped == null) {
      return List.of();
    }
    Object matched = wrapped.get("matchedActions");
    if (matched instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  public Map<String, Object> getFeeClearance(TenantScope scope, String admissionNo) {
    return get(
        properties.getIntegrations().getFeeBaseUrl() + "/api/fee/clearance/" + encode(admissionNo),
        scope);
  }

  public Map<String, Object> getLibraryClearance(TenantScope scope, String admissionNo) {
    return get(
        properties.getIntegrations().getLibraryBaseUrl()
            + "/api/library/clearance/"
            + encode(admissionNo),
        scope);
  }

  private static String encode(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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

  private Map<String, Object> post(String url, TenantScope scope, Map<String, Object> body) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .post()
              .uri(url)
              .headers(h -> applyTenant(h, scope))
              .body(body != null ? body : Map.of())
              .retrieve()
              .body(MAP_TYPE);
      return unwrap(envelope);
    } catch (RestClientResponseException ex) {
      log.warn("POST {} failed: {}", url, ex.getStatusCode());
      throw ex;
    } catch (Exception ex) {
      log.warn("POST {} failed: {}", url, ex.getMessage());
      throw new IllegalStateException("POST " + url + " failed: " + ex.getMessage(), ex);
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

  private static void applyTenant(
      org.springframework.http.HttpHeaders headers, TenantScope scope) {
    TenantHeaders.apply(headers, scope);
  }
}
