package com.sugamflow.school.exam.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.config.ExamProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AcademicClient {

  private static final Logger log = LoggerFactory.getLogger(AcademicClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final ExamProperties properties;

  public AcademicClient(RestClient.Builder restClientBuilder, ExamProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public Map<String, Object> getSection(TenantScope scope, String sectionId) {
    for (Map<String, Object> s : listSections(scope)) {
      if (sectionId != null && sectionId.equals(String.valueOf(s.get("id")))) {
        return s;
      }
    }
    return Map.of();
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listSections(TenantScope scope) {
    return list(scope, "/api/academic/sections");
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listSubjects(TenantScope scope) {
    return list(scope, "/api/academic/subjects");
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

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> list(TenantScope scope, String path) {
    String url = properties.getIntegrations().getAcademicBaseUrl() + path;
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
      log.warn("Academic {} failed: {}", path, ex.getMessage());
      return List.of();
    }
  }
}
