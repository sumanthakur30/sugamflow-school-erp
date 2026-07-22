package com.sugamflow.school.fee.payment;

import java.math.BigDecimal;
import java.util.Map;

/** Pluggable payment gateway adapter (SIMULATED / RAZORPAY). */
public interface PaymentGatewayAdapter {

  String adapterKey();

  GatewayOrderResult createOrder(
      String reference,
      BigDecimal amount,
      String currency,
      String studentRef,
      Map<String, Object> notes);

  /** Verify webhook HMAC; return false if invalid. */
  boolean verifyWebhook(String payloadBody, String signatureHeader);

  /** Extract gateway order id from a verified webhook JSON body, or null. */
  String extractOrderId(Map<String, Object> webhookBody);

  /** Extract payment/txn id from webhook body, or null. */
  String extractPaymentId(Map<String, Object> webhookBody);
}
