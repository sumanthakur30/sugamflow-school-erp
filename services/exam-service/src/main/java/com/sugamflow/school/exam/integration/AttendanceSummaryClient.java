package com.sugamflow.school.exam.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.config.ExamProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Fail-open attendance percentages for report cards. */
@Component
public class AttendanceSummaryClient {

  private static final Logger log = LoggerFactory.getLogger(AttendanceSummaryClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final ExamProperties properties;

  public AttendanceSummaryClient(RestClient.Builder restClientBuilder, ExamProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  /** Keys are student id and admission number, both pointing at the same summary row. */
  @SuppressWarnings("unchecked")
  public Map<String, Map<String, Object>> byStudent(TenantScope scope, UUID sectionId) {
    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    if (sectionId == null) {
      return out;
    }
    String url =
        properties.getIntegrations().getAttendanceBaseUrl()
            + "/api/attendance/roster/summary?sectionId="
            + sectionId;
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
        return out;
      }
      Object students = ((Map<String, Object>) data).get("students");
      if (!(students instanceof List<?> list)) {
        return out;
      }
      for (Object item : list) {
        if (!(item instanceof Map<?, ?> row)) {
          continue;
        }
        Map<String, Object> cast = (Map<String, Object>) row;
        put(out, cast.get("studentId"), cast);
        put(out, cast.get("admissionNo"), cast);
      }
    } catch (Exception ex) {
      log.warn("Attendance summary unavailable for {}: {}", sectionId, ex.getMessage());
    }
    return out;
  }

  private static void put(Map<String, Map<String, Object>> out, Object key, Map<String, Object> row) {
    if (key == null) {
      return;
    }
    String text = String.valueOf(key).trim();
    if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
      out.put(text.toLowerCase(), row);
    }
  }
}
