package com.sugamflow.school.library.integration;

import com.sugamflow.school.common.cache.TtlCache;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.library.config.LibraryProperties;
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
  private final LibraryProperties properties;

  public ConfigEngineClient(RestClient.Builder restClientBuilder, LibraryProperties properties) {
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

  public Map<String, Object> getWorkflow(TenantScope scope, String workflowKey) {
    return get(
        properties.getIntegrations().getWorkflowsBaseUrl() + "/api/workflows/" + workflowKey,
        scope);
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

  public Map<String, Object> previewNotification(
      TenantScope scope, String event, List<String> channels, String template) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event", event);
    payload.put("channels", channels);
    payload.put("template", template);
    payload.put("intent", template);
    Map<String, Object> result =
        post(
            properties.getIntegrations().getNotificationConfigBaseUrl()
                + "/api/school/notification-config/preview",
            scope,
            payload);
    return result != null ? result : Map.of();
  }

  public Map<String, Object> resolveNotification(
      TenantScope scope, String event, String intent, Map<String, Object> variables) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("event", event);
    payload.put("intent", intent);
    payload.put("variables", variables != null ? variables : Map.of());
    Map<String, Object> result =
        post(
            properties.getIntegrations().getNotificationConfigBaseUrl()
                + "/api/school/notification-config/templates/resolve",
            scope,
            payload);
    return result != null ? result : Map.of();
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

  private Map<String, Object> post(String url, TenantScope scope, Object body) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(h -> applyTenant(h, scope))
              .body(body)
              .retrieve()
              .body(MAP_TYPE);
      return unwrap(envelope);
    } catch (RestClientResponseException ex) {
      log.warn("POST {} failed: {}", url, ex.getStatusCode());
      return null;
    } catch (Exception ex) {
      log.warn("POST {} failed: {}", url, ex.getMessage());
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
    if (data instanceof List) {
      wrap.put("matchedActions", data);
    }
    return wrap;
  }

  private static void applyTenant(
      org.springframework.http.HttpHeaders headers, TenantScope scope) {
    TenantHeaders.apply(headers, scope);
  }
}
