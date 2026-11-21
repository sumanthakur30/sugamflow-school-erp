package com.sugamflow.school.fee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.fee.config.FeeProperties;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.integration.StudentProfileClient;
import com.sugamflow.school.fee.payment.PaymentGatewayRegistry;
import com.sugamflow.school.fee.payment.SimulatedPaymentAdapter;
import com.sugamflow.school.fee.persistence.entity.FinanceTransactionEntity;
import com.sugamflow.school.fee.persistence.repo.FinanceDefinitionRepository;
import com.sugamflow.school.fee.persistence.repo.FinanceTransactionRepository;
import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceServiceWebhookIdempotencyTest {

  @Mock private FinanceDefinitionRepository definitionRepo;
  @Mock private FinanceTransactionRepository transactionRepo;
  @Mock private ConfigEngineClient engines;
  @Mock private FeeProperties properties;
  @Mock private PaymentGatewayRegistry gatewayRegistry;
  @Mock private FeeCollectionService feeCollections;
  @Mock private StudentProfileClient studentProfiles;
  @Mock private StudentFeeAdjustmentService feeAdjustments;

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void rejectsAWebhookWhoseSignatureDoesNotMatch() {
    FinanceService service = service();
    when(gatewayRegistry.byKey("simulated")).thenReturn(new SimulatedPaymentAdapter());

    FeeException ex =
        assertThrows(
            FeeException.class,
            () -> service.handleWebhook("simulated", "{\"gatewayOrderId\":\"order-1\"}", "nope"));

    assertEquals("WEBHOOK_INVALID", ex.getCode());
    verifyNoInteractions(transactionRepo);
  }

  @Test
  void aSecondWebhookDoesNotCreateAnotherReceipt() {
    FinanceService service = service();
    when(gatewayRegistry.byKey("simulated")).thenReturn(new SimulatedPaymentAdapter());
    FinanceTransactionEntity intent = pendingIntent();
    when(transactionRepo.findPaymentIntentByGatewayOrderId("order-1")).thenReturn(Optional.of(intent));
    when(transactionRepo.findByOrganizationIdAndIdempotencyKey(any(), any()))
        .thenReturn(Optional.empty());
    AtomicInteger captures = new AtomicInteger();
    when(transactionRepo.save(any()))
        .thenAnswer(
            invocation -> {
              FinanceTransactionEntity saved = invocation.getArgument(0);
              if ("PAYMENT_CAPTURE".equals(saved.getTransactionType())) {
                captures.incrementAndGet();
              }
              return saved;
            });

    var first =
        service.handleWebhook(
            "simulated",
            "{\"gatewayOrderId\":\"order-1\",\"gatewayTxnId\":\"pay-1\"}",
            "sim:ok");
    var second =
        service.handleWebhook(
            "simulated",
            "{\"gatewayOrderId\":\"order-1\",\"gatewayTxnId\":\"pay-2\"}",
            "sim:ok");

    assertEquals("CAPTURED", first.get("status"));
    assertEquals(first.get("id"), second.get("id"));
    assertEquals("pay-1", intent.getPayload().get("gatewayTxnId"));
    assertEquals(1, captures.get());
    verifyNoInteractions(feeCollections);
  }

  private FinanceService service() {
    return new FinanceService(
        definitionRepo,
        transactionRepo,
        engines,
        properties,
        gatewayRegistry,
        feeCollections,
        studentProfiles,
        feeAdjustments,
        new ObjectMapper());
  }

  private static FinanceTransactionEntity pendingIntent() {
    FinanceTransactionEntity intent = new FinanceTransactionEntity();
    intent.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    intent.setOrganizationId("SCH-A");
    intent.setBranchId("main");
    intent.setAcademicSessionId("2026-27");
    intent.setTransactionType("PAYMENT_INTENT");
    intent.setStatus("PENDING");
    intent.setStudentRef("ADM-1");
    intent.setCurrency("INR");
    intent.setGrossAmount(new BigDecimal("2500.00"));
    intent.setNetAmount(new BigDecimal("2500.00"));
    intent.setReferenceNo("PI-TEST");
    intent.setIdempotencyKey("fee-pay-1");
    intent.setPayload(new LinkedHashMap<>());
    intent.getPayload().put("gatewayOrderId", "order-1");
    intent.getPayload().put("adapter", "SIMULATED");
    return intent;
  }
}
