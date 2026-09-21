package com.sugamflow.school.subscription.payment;

import com.sugamflow.school.subscription.config.SubscriptionPaymentProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
 * Razorpay Orders API for subscription invoices. When keys are blank (or mode=simulate), returns a
 * simulated checkout payload so local/dev still works.
 */
@Component
public class SubscriptionRazorpayClient {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionRazorpayClient.class);
  private static final String ORDERS_URL = "https://api.razorpay.com/v1/orders";

  private final SubscriptionPaymentProperties properties;
  private final RestClient.Builder restClientBuilder;

  public SubscriptionRazorpayClient(
      SubscriptionPaymentProperties properties, RestClient.Builder subscriptionRestClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = subscriptionRestClientBuilder;
  }

  public boolean isLiveConfigured() {
    var rz = properties.getRazorpay();
    return rz.getKeyId() != null
        && !rz.getKeyId().isBlank()
        && rz.getKeySecret() != null
        && !rz.getKeySecret().isBlank();
  }

  public boolean shouldUseLive() {
    String mode = properties.getMode() == null ? "auto" : properties.getMode().trim().toLowerCase(Locale.ROOT);
    if ("simulate".equals(mode)) {
      return false;
    }
    if ("razorpay".equals(mode)) {
      return true;
    }
    return isLiveConfigured();
  }

  public Map<String, Object> createOrder(long amountMinor, String currency, String receipt, Map<String, Object> notes) {
    if (amountMinor < 100) {
      throw new IllegalArgumentException("Amount must be at least 100 paise (₹1)");
    }
    if (!shouldUseLive()) {
      Map<String, Object> sim = new LinkedHashMap<>();
      sim.put("checkoutMode", "SIMULATED");
      sim.put("provider", "SIMULATED");
      sim.put("razorpayKeyId", "rzp_test_simulated");
      sim.put("razorpayOrderId", "order_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
      sim.put("amountMinor", amountMinor);
      sim.put("currency", currency == null || currency.isBlank() ? "INR" : currency);
      sim.put("receipt", receipt);
      return sim;
    }
    if (!isLiveConfigured()) {
      throw new IllegalStateException(
          "Razorpay keys not configured (subscription.payment.razorpay.key-id / key-secret)");
    }

    var rz = properties.getRazorpay();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("amount", amountMinor);
    body.put("currency", currency == null || currency.isBlank() ? "INR" : currency);
    body.put("receipt", receipt == null ? "sub" : (receipt.length() > 40 ? receipt.substring(0, 40) : receipt));
    body.put("notes", notes == null ? Map.of() : notes);

    String auth =
        Base64.getEncoder()
            .encodeToString((rz.getKeyId() + ":" + rz.getKeySecret()).getBytes(StandardCharsets.UTF_8));
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
        throw new IllegalStateException("Razorpay order response missing id");
      }
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("checkoutMode", "RAZORPAY_CHECKOUT");
      out.put("provider", "RAZORPAY");
      out.put("razorpayKeyId", rz.getKeyId());
      out.put("razorpayOrderId", String.valueOf(response.get("id")));
      out.put("amountMinor", amountMinor);
      out.put("currency", body.get("currency"));
      out.put("receipt", body.get("receipt"));
      return out;
    } catch (RestClientResponseException ex) {
      log.warn("Razorpay order create failed: {} {}", ex.getStatusCode(), ex.getResponseBodyAsString());
      throw new IllegalStateException("Razorpay order create failed: " + ex.getStatusCode().value());
    }
  }

  /** Checkout success signature: HMAC_SHA256(orderId|paymentId, key_secret). */
  public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
    if (!shouldUseLive()) {
      return true;
    }
    String secret = properties.getRazorpay().getKeySecret();
    if (secret == null || secret.isBlank() || orderId == null || paymentId == null || signature == null) {
      return false;
    }
    String payload = orderId + "|" + paymentId;
    return constantTimeEquals(hmacHex(secret, payload), signature.trim());
  }

  public boolean verifyWebhookSignature(String body, String signatureHeader) {
    if (!shouldUseLive()) {
      return true;
    }
    String secret = properties.getRazorpay().getWebhookSecret();
    if (secret == null || secret.isBlank() || body == null || signatureHeader == null) {
      return false;
    }
    return constantTimeEquals(hmacHex(secret, body), signatureHeader.trim());
  }

  private static String hmacHex(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(raw.length * 2);
      for (byte b : raw) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("HMAC failed: " + ex.getMessage(), ex);
    }
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
