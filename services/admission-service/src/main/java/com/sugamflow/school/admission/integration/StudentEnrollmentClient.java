package com.sugamflow.school.admission.integration;

import com.sugamflow.school.admission.config.AdmissionProperties;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.tenant.TenantHeaders;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Calls student-service to enroll from an approved admission application. */
@Component
public class StudentEnrollmentClient {

  private static final Logger log = LoggerFactory.getLogger(StudentEnrollmentClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final AdmissionProperties properties;

  public StudentEnrollmentClient(
      RestClient.Builder restClientBuilder, AdmissionProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public Map<String, Object> enrollFromAdmission(
      TenantScope scope, Map<String, Object> payload) {
    String url =
        properties.getIntegrations().getStudentBaseUrl() + "/api/student/enroll-from-admission";
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(
                  h -> TenantHeaders.apply(h, scope))
              .body(payload)
              .retrieve()
              .body(MAP_TYPE);
      return unwrap(envelope);
    } catch (RestClientResponseException ex) {
      log.warn("Enrollment failed {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("status", "FAILED");
      err.put("error", ex.getStatusCode() + " " + ex.getResponseBodyAsString());
      return err;
    } catch (Exception ex) {
      log.warn("Enrollment failed: {}", ex.getMessage());
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("status", "FAILED");
      err.put("error", ex.getMessage());
      return err;
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
}
