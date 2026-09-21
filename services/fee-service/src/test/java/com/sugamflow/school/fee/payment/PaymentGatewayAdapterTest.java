package com.sugamflow.school.fee.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.fee.config.FeeProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulatedPaymentAdapterTest {

  @Test
  void createOrderReturnsSimulatedCheckout() {
    SimulatedPaymentAdapter adapter = new SimulatedPaymentAdapter();
    GatewayOrderResult order =
        adapter.createOrder("PI-ABC", BigDecimal.TEN, "INR", "ADM-1", Map.of());
    assertEquals("SIMULATED", order.adapter());
    assertTrue(String.valueOf(order.checkout().get("simulatedCheckoutUrl")).contains("PI-ABC"));
  }

  @Test
  void webhookRequiresSimSignature() {
    SimulatedPaymentAdapter adapter = new SimulatedPaymentAdapter();
    assertFalse(adapter.verifyWebhook("{}", null));
    assertTrue(adapter.verifyWebhook("{}", "sim:ok"));
  }
}

class PaymentGatewayRegistryTest {

  @Test
  void simulateModeAlwaysReturnsSimulated() {
    FeeProperties props = new FeeProperties();
    props.getPayment().setMode("simulate");
    PaymentGatewayRegistry registry =
        new PaymentGatewayRegistry(
            props, new SimulatedPaymentAdapter(), new RazorpayPaymentAdapter(props, null));
    assertEquals(
        "SIMULATED",
        registry.resolve(Map.of("adapter", "RAZORPAY")).adapterKey());
  }
}
