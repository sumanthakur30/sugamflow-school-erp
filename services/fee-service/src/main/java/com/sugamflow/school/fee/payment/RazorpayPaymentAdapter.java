package com.sugamflow.school.fee.payment;

import com.sugamflow.school.fee.config.FeeProperties;
import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Razorpay Orders API adapter. When key/secret are blank, createOrder fails closed so callers can
 * fall back to SIMULATED via {@link PaymentGatewayRegistry}.
 */
@Component
public class RazorpayPaymentAdapter implements PaymentGatewayAdapter {

  private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentAdapter.class);
  private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

  private final FeeProperties properties;
  private final RestClient.Builder restClientBuilder;

  public RazorpayPaymentAdapter(FeeProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  @Override
  public String adapterKey() {
    return "RAZORPAY";
  }

  public boolean isConfigured() {
    FeeProperties.Payment.Razorpay rz = properties.getPayment().getRazorpay();
    return rz.getKeyId() != null
        && !rz.getKeyId().isBlank()
        && rz.getKeySecret() != null
        && !rz.getKeySecret().isBlank();
  }

  @Override
  public GatewayOrderResult createOrder(
      String reference,
      BigDecimal amount,
      String currency,
      String studentRef,
      Map<String, Object> notes) {
    if (!isConfigured()) {
      throw new FeeException(
          "GATEWAY_NOT_CONFIGURED",
          "Razorpay keys are not configured (fee.payment.razorpay.key-id / key-secret)");
    }
    FeeProperties.Payment.Razorpay rz = properties.getPayment().getRazorpay();
    long amountPaise =
        amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    if (amountPaise < 100) {
      throw new FeeException("VALIDATION", "Razorpay amount must be at least 1.00 INR");
    }

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", amountPaise);
    body.put("currency", currency == null || currency.isBlank() ? "INR" : currency);
    body.put("receipt", reference.length() > 40 ? reference.substring(0, 40) : reference);
    body.put("notes", notes == null ? Map.of() : notes);

    String auth =
        Base64.getEncoder()
            .encodeToString(
                (rz.getKeyId() + ":" + rz.getKeySecret()).getBytes(StandardCharsets.UTF_8));
    try {
      Map<String, Object> response =
          restClientBuilder
              .build()
              .post()
              .uri(ORDERS_URL)
              .contentType(MediaType.APPLICATION_JSON)
              .header("Authorization", "Basic " + auth)
              .body(body)
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      if (response == null || response.get("id") == null) {
        throw new FeeException("GATEWAY_ERROR", "Razorpay order response missing id");
      }
      String orderId = String.valueOf(response.get("id"));
      return GatewayOrderResult.razorpay(
          orderId, rz.getKeyId(), amountPaise, String.valueOf(body.get("currency")), reference);
    } catch (RestClientResponseException ex) {
      log.warn("Razorpay order create failed: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
      throw new FeeException(
          "GATEWAY_ERROR", "Razorpay order create failed: " + ex.getStatusCode().value());
    } catch (FeeException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("Razorpay order create failed: {}", ex.getMessage());
      throw new FeeException("GATEWAY_ERROR", "Razorpay order create failed: " + ex.getMessage());
    }
  }

  @Override
  public boolean verifyWebhook(String payloadBody, String signatureHeader) {
    String secret = properties.getPayment().getRazorpay().getWebhookSecret();
    if (secret == null || secret.isBlank()) {
      log.warn("Razorpay webhook secret not configured");
      return false;
    }
    if (payloadBody == null || signatureHeader == null || signatureHeader.isBlank()) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(payloadBody.getBytes(StandardCharsets.UTF_8));
      String expected = bytesToHex(raw);
      return constantTimeEquals(expected, signatureHeader.trim());
    } catch (Exception ex) {
      log.warn("Razorpay webhook verify failed: {}", ex.getMessage());
      return false;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public String extractOrderId(Map<String, Object> webhookBody) {
    Object payload = webhookBody.get("payload");
    if (!(payload instanceof Map<?, ?> payloadMap)) {
      return null;
    }
    Object payment = payloadMap.get("payment");
    if (payment instanceof Map<?, ?> paymentWrap) {
      Object entity = paymentWrap.get("entity");
      if (entity instanceof Map<?, ?> ent && ent.get("order_id") != null) {
        return String.valueOf(ent.get("order_id"));
      }
    }
    Object order = payloadMap.get("order");
    if (order instanceof Map<?, ?> orderWrap) {
      Object entity = orderWrap.get("entity");
      if (entity instanceof Map<?, ?> ent && ent.get("id") != null) {
        return String.valueOf(ent.get("id"));
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public String extractPaymentId(Map<String, Object> webhookBody) {
    Object payload = webhookBody.get("payload");
    if (!(payload instanceof Map<?, ?> payloadMap)) {
      return null;
    }
    Object payment = payloadMap.get("payment");
    if (payment instanceof Map<?, ?> paymentWrap) {
      Object entity = paymentWrap.get("entity");
      if (entity instanceof Map<?, ?> ent && ent.get("id") != null) {
        return String.valueOf(ent.get("id"));
      }
    }
    return null;
  }

  /** True when event is a successful capture/paid notification. */
  public boolean isCaptureEvent(Map<String, Object> webhookBody) {
    String event = webhookBody.get("event") == null ? "" : String.valueOf(webhookBody.get("event"));
    return "payment.captured".equals(event) || "order.paid".equals(event);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null || a.length() != b.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }
}
