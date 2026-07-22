package com.sugamflow.school.attendance.integration;

import com.sugamflow.school.attendance.config.AttendanceProperties;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Academic section + teacher-scope lookups. */
@Component
public class AcademicClient {

  private static final Logger log = LoggerFactory.getLogger(AcademicClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final AttendanceProperties properties;

  public AcademicClient(RestClient.Builder restClientBuilder, AttendanceProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public Map<String, Object> getSection(TenantScope scope, String sectionId) {
    List<Map<String, Object>> sections = listSections(scope);
    for (Map<String, Object> s : sections) {
      if (sectionId != null && sectionId.equals(String.valueOf(s.get("id")))) {
        return s;
      }
    }
    return Map.of();
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listSections(TenantScope scope) {
    String url = properties.getIntegrations().getAcademicBaseUrl() + "/api/academic/sections";
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope == null) {
        return List.of();
      }
      Object data = envelope.get("data");
      if (!(data instanceof List<?> list)) {
        return List.of();
      }
      return (List<Map<String, Object>>) (List<?>) list;
    } catch (Exception ex) {
      log.warn("Academic sections lookup failed: {}", ex.getMessage());
      return List.of();
    }
  }

  public Map<String, Object> teacherScope(TenantScope scope) {
    String url = properties.getIntegrations().getAcademicBaseUrl() + "/api/academic/teacher-scope";
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope == null || !(envelope.get("data") instanceof Map<?, ?> data)) {
        return new LinkedHashMap<>();
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> cast = (Map<String, Object>) data;
      return cast;
    } catch (Exception ex) {
      log.warn("Teacher scope lookup failed: {}", ex.getMessage());
      return new LinkedHashMap<>();
    }
  }
}
