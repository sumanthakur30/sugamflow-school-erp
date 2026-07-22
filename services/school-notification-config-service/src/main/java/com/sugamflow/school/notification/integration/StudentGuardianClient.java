package com.sugamflow.school.notification.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.notification.config.NotificationConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class StudentGuardianClient {

  private static final Logger log = LoggerFactory.getLogger(StudentGuardianClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final NotificationConfigProperties properties;

  public StudentGuardianClient(
      RestClient.Builder restClientBuilder, NotificationConfigProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> deliveryTargets(TenantScope scope) {
    String url =
        properties.getIntegrations().getStudentBaseUrl() + "/api/student/guardians/delivery-targets";
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope != null && envelope.get("data") instanceof List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
          if (item instanceof Map<?, ?> map) {
            out.add((Map<String, Object>) map);
          }
        }
        return out;
      }
    } catch (Exception ex) {
      log.warn("Guardian delivery-target lookup failed: {}", ex.getMessage());
    }
    return List.of();
  }
}
