package com.sugamflow.school.fee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.config.FeeProperties;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.integration.StudentProfileClient;
import com.sugamflow.school.fee.payment.PaymentGatewayRegistry;
import com.sugamflow.school.fee.persistence.repo.FinanceDefinitionRepository;
import com.sugamflow.school.fee.persistence.repo.FinanceTransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceServiceStandardFeeHistoryTest {

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
  void savingANewStandardFeeDoesNotReadOrWriteIssuedDemands() {
    FinanceService service =
        new FinanceService(
            definitionRepo,
            transactionRepo,
            engines,
            properties,
            gatewayRegistry,
            feeCollections,
            studentProfiles,
            feeAdjustments,
            new ObjectMapper());
    TenantContext.set(new TenantScope("SCH-A", "main", "2026-27", "admin", "ADMIN"));
    when(engines.isFeatureEnabled(any(), eq(FeeCollectionService.FEATURE_FEE))).thenReturn(true);
    when(definitionRepo.findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
            any(), any(), eq("ACTIVE")))
        .thenReturn(List.of());
    when(definitionRepo
            .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                any(), any(), any(), eq("ACTIVE")))
        .thenReturn(Optional.empty());
    when(definitionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Map<String, Object> saved =
        service.saveStandardFees(
            Map.of(
                "academicSessionId",
                "2026-27",
                "categories",
                List.of(Map.of("headKey", "TUITION", "label", "Tuition Fee", "frequency", "M")),
                "classes",
                List.of(
                    Map.of(
                        "className",
                        "Nursery",
                        "lines",
                        List.of(Map.of("headKey", "TUITION", "amount", 800, "active", true))))));

    assertEquals(
        "Saved. Fee receipts and dues already recorded are unchanged.", saved.get("message"));
    verifyNoInteractions(transactionRepo);
  }
}
