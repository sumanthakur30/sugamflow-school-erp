package com.sugamflow.school.student.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.config.StudentProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Best-effort staff lookup for teacher class assignments. */
@Component
public class StaffClient {

  private static final Logger log = LoggerFactory.getLogger(StaffClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final StudentProperties properties;

  public StaffClient(RestClient.Builder restClientBuilder, StudentProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> listStaff(TenantScope scope, int size) {
    String url =
        properties.getIntegrations().getStaffBaseUrl()
            + "/api/staff/staff?page=0&size="
            + Math.max(1, Math.min(size, 500));
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
    } catch (RestClientResponseException ex) {
      log.warn("Staff list failed: {}", ex.getStatusCode());
      return List.of();
    } catch (Exception ex) {
      log.warn("Staff list failed: {}", ex.getMessage());
      return List.of();
    }
  }
}
