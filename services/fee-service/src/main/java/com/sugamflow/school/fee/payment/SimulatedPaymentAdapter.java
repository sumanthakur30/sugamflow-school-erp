package com.sugamflow.school.fee.payment;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Local/dev adapter — no external calls; capture via simulate-capture API. */
@Component
public class SimulatedPaymentAdapter implements PaymentGatewayAdapter {

  @Override
  public String adapterKey() {
    return "SIMULATED";
  }

  @Override
  public GatewayOrderResult createOrder(
      String reference,
      BigDecimal amount,
      String currency,
      String studentRef,
      Map<String, Object> notes) {
    String url =
        "https://pay.example.local/checkout/" + reference + "?amount=" + amount + "&ccy=" + currency;
    return GatewayOrderResult.simulated(reference, amount, currency, url);
  }

  @Override
  public boolean verifyWebhook(String payloadBody, String signatureHeader) {
    // Simulated webhooks are accepted only when signed with the shared simulate secret.
    return signatureHeader != null && signatureHeader.startsWith("sim:");
  }

  @Override
  public String extractOrderId(Map<String, Object> webhookBody) {
    Object orderId = webhookBody.get("gatewayOrderId");
    if (orderId != null) {
      return String.valueOf(orderId);
    }
    Object payload = webhookBody.get("payload");
    if (payload instanceof Map<?, ?> m && m.get("gatewayOrderId") != null) {
      return String.valueOf(m.get("gatewayOrderId"));
    }
    return null;
  }

  @Override
  public String extractPaymentId(Map<String, Object> webhookBody) {
    Object id = webhookBody.get("gatewayTxnId");
    return id == null ? null : String.valueOf(id);
  }
}
