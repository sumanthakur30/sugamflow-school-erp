package com.sugamflow.school.attendance.integration;

import com.sugamflow.school.attendance.config.AttendanceProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NotificationDeliveryClient {

  private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryClient.class);

  private final RestClient.Builder restClientBuilder;
  private final AttendanceProperties properties;

  public NotificationDeliveryClient(
      RestClient.Builder restClientBuilder, AttendanceProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public Map<String, Object> queue(
      String shopId, String channel, String recipient, String subject, String body) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("shopId", shopId);
    payload.put("channel", channel);
    payload.put("recipient", recipient);
    payload.put("subject", subject != null && !subject.isBlank() ? subject : channel);
    payload.put("body", body != null && !body.isBlank() ? body : subject);

    String url =
        properties.getIntegrations().getNotificationDeliveryBaseUrl() + "/api/v1/notifications";
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          restClientBuilder
              .build()
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .body(payload)
              .retrieve()
              .body(Map.class);
      return response != null ? response : Map.of("status", "UNKNOWN");
    } catch (RestClientResponseException ex) {
      log.warn("Notification delivery failed {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("status", "FAILED");
      err.put("error", ex.getStatusCode() + " " + ex.getResponseBodyAsString());
      return err;
    } catch (Exception ex) {
      log.warn("Notification delivery failed: {}", ex.getMessage());
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("status", "FAILED");
      err.put("error", ex.getMessage());
      return err;
    }
  }
}
