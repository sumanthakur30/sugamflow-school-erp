package com.sugamflow.school.fee.payment;

import com.sugamflow.school.fee.config.FeeProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves SIMULATED vs RAZORPAY from provider definition + fee.payment.mode. */
@Component
public class PaymentGatewayRegistry {

  private final FeeProperties properties;
  private final SimulatedPaymentAdapter simulated;
  private final RazorpayPaymentAdapter razorpay;

  public PaymentGatewayRegistry(
      FeeProperties properties,
      SimulatedPaymentAdapter simulated,
      RazorpayPaymentAdapter razorpay) {
    this.properties = properties;
    this.simulated = simulated;
    this.razorpay = razorpay;
  }

  public PaymentGatewayAdapter resolve(Map<String, Object> providerDefinition) {
    String mode = normalize(properties.getPayment().getMode());
    if ("simulate".equals(mode) || "simulated".equals(mode)) {
      return simulated;
    }
    String adapter =
        providerDefinition == null
            ? "SIMULATED"
            : normalize(String.valueOf(providerDefinition.getOrDefault("adapter", "SIMULATED")));
    if ("razorpay".equals(adapter) && razorpay.isConfigured()) {
      return razorpay;
    }
    // Force razorpay mode without keys → still fail closed at createOrder; prefer simulated when
    // mode=auto and keys missing.
    if ("auto".equals(mode) || mode.isBlank()) {
      if ("razorpay".equals(adapter) && razorpay.isConfigured()) {
        return razorpay;
      }
      return simulated;
    }
    if ("razorpay".equals(mode) || "razorpay".equals(adapter)) {
      return razorpay;
    }
    return simulated;
  }

  public PaymentGatewayAdapter byKey(String adapterKey) {
    String key = normalize(adapterKey);
    if ("razorpay".equals(key)) {
      return razorpay;
    }
    return simulated;
  }

  public List<String> availableAdapters() {
    return razorpay.isConfigured() ? List.of("SIMULATED", "RAZORPAY") : List.of("SIMULATED");
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }
}
