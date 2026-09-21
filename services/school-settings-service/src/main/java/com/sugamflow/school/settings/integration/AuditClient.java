package com.sugamflow.school.settings.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.settings.config.SettingsProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AuditClient {

  public static final String SKIP_HEADER = "X-Skip-Config-Audit";

  private static final Logger log = LoggerFactory.getLogger(AuditClient.class);

  private final RestClient.Builder restClientBuilder;
  private final SettingsProperties properties;
  private final ObjectMapper objectMapper;

  public AuditClient(
      RestClient.Builder restClientBuilder,
      SettingsProperties properties,
      ObjectMapper objectMapper) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public void recordChange(
      String entityType, String entityKey, Object oldValue, Object newValue, String reason) {
    if (shouldSkip()) {
      return;
    }
    TenantScope scope = TenantContext.get().orElse(null);
    if (scope == null) {
      return;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("entityType", entityType);
    body.put("entityKey", entityKey);
    body.put("oldValue", toAuditMap(oldValue));
    body.put("newValue", toAuditMap(newValue));
    body.put("reason", reason);
    body.put("status", "PENDING_APPROVAL");
    try {
      restClientBuilder
          .build()
          .post()
          .uri(properties.getIntegrations().getAuditBaseUrl() + "/api/audit/config-changes")
          .contentType(MediaType.APPLICATION_JSON)
          .headers(
              h -> TenantHeaders.apply(h, scope))
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException ex) {
      log.warn("Audit record failed: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
    } catch (Exception ex) {
      log.warn("Audit record failed: {}", ex.getMessage());
    }
  }

  private boolean shouldSkip() {
    try {
      var attrs =
          org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
      if (attrs
          instanceof
          org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
        String skip = servletAttrs.getRequest().getHeader(SKIP_HEADER);
        return "true".equalsIgnoreCase(skip) || "1".equals(skip);
      }
    } catch (Exception ignored) {
      // no request context
    }
    return false;
  }

  private Map<String, Object> toAuditMap(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Map<?, ?> map) {
      return objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
    }
    if (value instanceof List<?> list) {
      Map<String, Object> wrap = new LinkedHashMap<>();
      wrap.put("nodes", objectMapper.convertValue(list, new TypeReference<List<Object>>() {}));
      return wrap;
    }
    return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
  }
}
