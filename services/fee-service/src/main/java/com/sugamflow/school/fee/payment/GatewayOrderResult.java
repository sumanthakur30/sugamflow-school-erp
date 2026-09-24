package com.sugamflow.school.fee.payment;

import java.math.BigDecimal;
import java.util.Map;

/** Result of creating a provider-side checkout order. */
public record GatewayOrderResult(
    String adapter,
    String gatewayOrderId,
    String publicKey,
    String checkoutMode,
    Map<String, Object> checkout,
    Map<String, Object> providerMeta) {

  public static GatewayOrderResult simulated(
      String reference, BigDecimal amount, String currency, String checkoutUrl) {
    return new GatewayOrderResult(
        "SIMULATED",
        "SIM-ORD-" + reference,
        null,
        "SIMULATED",
        Map.of(
            "simulatedCheckoutUrl", checkoutUrl,
            "amount", amount,
            "currency", currency,
            "reference", reference),
        Map.of("simulated", true));
  }

  public static GatewayOrderResult razorpay(
      String orderId, String keyId, long amountPaise, String currency, String receipt) {
    return new GatewayOrderResult(
        "RAZORPAY",
        orderId,
        keyId,
        "RAZORPAY_CHECKOUT",
        Map.of(
            "razorpayOrderId", orderId,
            "razorpayKeyId", keyId,
            "amountPaise", amountPaise,
            "currency", currency,
            "receipt", receipt,
            "name", "School Fee Payment"),
        Map.of("razorpayOrderId", orderId));
  }
}
