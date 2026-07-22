package com.sugamflow.school.student.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.config.StudentProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Best-effort lookup of the class/section labels a teacher owns in academic-structure-service.
 * Returns the union of student labels, section names and codes so RBAC can match free-text student
 * class fields.
 */
@Component
public class AcademicClient {

  private static final Logger log = LoggerFactory.getLogger(AcademicClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final StudentProperties properties;

  public AcademicClient(RestClient.Builder restClientBuilder, StudentProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  /** Class/section labels for a teacher (studentLabels + sectionNames + sectionCodes). */
  public Set<String> teacherClassLabels(TenantScope scope, String username) {
    Set<String> labels = new LinkedHashSet<>();
    String url = properties.getIntegrations().getAcademicBaseUrl() + "/api/academic/teacher-scope";
    if (username != null && !username.isBlank()) {
      url += "?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8);
    }
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
        return labels;
      }
      Object data = envelope.get("data");
      if (!(data instanceof Map<?, ?> map)) {
        return labels;
      }
      addAll(labels, map.get("studentLabels"));
      addAll(labels, map.get("sectionNames"));
      addAll(labels, map.get("sectionCodes"));
      return labels;
    } catch (RestClientResponseException ex) {
      log.warn("Teacher scope lookup failed: {}", ex.getStatusCode());
      return labels;
    } catch (Exception ex) {
      log.warn("Teacher scope lookup failed: {}", ex.getMessage());
      return labels;
    }
  }

  private static void addAll(Set<String> into, Object raw) {
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item != null && !String.valueOf(item).isBlank()) {
          into.add(String.valueOf(item).trim());
        }
      }
    }
  }
}
