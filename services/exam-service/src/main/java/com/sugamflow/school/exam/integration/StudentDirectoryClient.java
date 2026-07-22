package com.sugamflow.school.exam.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.config.ExamProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class StudentDirectoryClient {

  private static final Logger log = LoggerFactory.getLogger(StudentDirectoryClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final ExamProperties properties;

  public StudentDirectoryClient(RestClient.Builder restClientBuilder, ExamProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listByClassSection(TenantScope scope, String classSection, int size) {
    if (classSection == null || classSection.isBlank()) {
      return List.of();
    }
    String url =
        properties.getIntegrations().getStudentBaseUrl()
            + "/api/student/directory/students?page=0&size="
            + Math.max(1, Math.min(size, 500))
            + "&classSection="
            + URLEncoder.encode(classSection.trim(), StandardCharsets.UTF_8);
    return pageItems(scope, url);
  }

  /** Relationship-filtered student list (parents see linked children only). */
  public List<Map<String, Object>> listAccessible(TenantScope scope, int size) {
    String url =
        properties.getIntegrations().getStudentBaseUrl()
            + "/api/student/students?page=0&size="
            + Math.max(1, Math.min(size, 200));
    return pageItems(scope, url);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> pageItems(TenantScope scope, String url) {
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
      if (!(data instanceof Map<?, ?> page)) {
        return List.of();
      }
      Object items = page.get("items");
      if (!(items instanceof List<?> list)) {
        return List.of();
      }
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          out.add((Map<String, Object>) m);
        }
      }
      return out;
    } catch (Exception ex) {
      log.warn("Student directory lookup failed: {}", ex.getMessage());
      return List.of();
    }
  }
}
