package com.sugamflow.school.hostel.integration;

import com.sugamflow.school.hostel.config.HostelProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NotificationDeliveryClient {

  private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryClient.class);

  private final RestClient.Builder restClientBuilder;
  private final HostelProperties properties;
  private final String internalApiKey;

  public NotificationDeliveryClient(
      RestClient.Builder restClientBuilder,
      HostelProperties properties,
      @Value("${security.jwt.internal-api-key:${SECURITY_INTERNAL_API_KEY:${SECURITY_INVITE_INTERNAL_KEY:dev-invite-key-change-in-production}}}")
          String internalApiKey) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
    this.internalApiKey = internalApiKey == null ? "" : internalApiKey.trim();
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
      var spec =
          restClientBuilder
              .build()
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON);
      if (!internalApiKey.isBlank()) {
        spec = spec.header("X-Internal-Api-Key", internalApiKey);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> response = spec.body(payload).retrieve().body(Map.class);
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
