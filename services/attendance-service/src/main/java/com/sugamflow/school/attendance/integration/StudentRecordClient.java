package com.sugamflow.school.attendance.integration;

import com.sugamflow.school.attendance.config.AttendanceProperties;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Full student record lookup (guardians + contacts) for parent alerts. */
@Component
public class StudentRecordClient {

  private static final Logger log = LoggerFactory.getLogger(StudentRecordClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final AttendanceProperties properties;

  public StudentRecordClient(RestClient.Builder restClientBuilder, AttendanceProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  /** Returns the full student DTO (with {@code guardians} and {@code answers}) or empty map. */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getStudent(TenantScope scope, String studentId) {
    if (studentId == null || studentId.isBlank()) {
      return Map.of();
    }
    String url =
        properties.getIntegrations().getStudentBaseUrl() + "/api/student/students/" + studentId;
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope != null && envelope.get("data") instanceof Map<?, ?> data) {
        return (Map<String, Object>) data;
      }
      return Map.of();
    } catch (Exception ex) {
      log.warn("Student record lookup failed for {}: {}", studentId, ex.getMessage());
      return Map.of();
    }
  }
}
