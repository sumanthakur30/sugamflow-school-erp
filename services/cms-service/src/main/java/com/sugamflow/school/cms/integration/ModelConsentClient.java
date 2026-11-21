package com.sugamflow.school.cms.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Fail closed. External website drafts run only after this school has granted model consent. */
@Component
public class ModelConsentClient {

  private static final Logger log = LoggerFactory.getLogger(ModelConsentClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final String studentBaseUrl;

  public ModelConsentClient(
      RestClient.Builder restClientBuilder,
      @Value("${cms.integrations.student-base-url:http://localhost:8191}") String studentBaseUrl) {
    this.restClientBuilder = restClientBuilder;
    this.studentBaseUrl = studentBaseUrl == null ? "" : studentBaseUrl.replaceAll("/$", "");
  }

  public boolean allowed(String organizationId) {
    if (studentBaseUrl.isBlank() || organizationId == null || organizationId.isBlank()) {
      return false;
    }
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(studentBaseUrl + "/api/student/privacy/model-and-bulk")
              .headers(h -> TenantHeaders.apply(h, new TenantScope(organizationId, null, null, null, null)))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope == null) {
        return false;
      }
      Object data = envelope.get("data");
      Object allowed = data instanceof Map<?, ?> map ? map.get("allowed") : envelope.get("allowed");
      return Boolean.TRUE.equals(allowed);
    } catch (Exception ex) {
      log.info("Model consent check failed closed: {}", ex.getMessage());
      return false;
    }
  }
}
