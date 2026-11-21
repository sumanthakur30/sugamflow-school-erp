package com.sugamflow.school.fee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.config.FeeProperties;
import com.sugamflow.school.fee.finance.FinanceCatalog;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.integration.StudentProfileClient;
import com.sugamflow.school.fee.payment.PaymentGatewayRegistry;
import com.sugamflow.school.fee.persistence.entity.FinanceDefinitionEntity;
import com.sugamflow.school.fee.persistence.repo.FinanceDefinitionRepository;
import com.sugamflow.school.fee.persistence.repo.FinanceTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceServiceDemandRulesTest {

  @Mock private FinanceDefinitionRepository definitionRepo;
  @Mock private FinanceTransactionRepository transactionRepo;
  @Mock private ConfigEngineClient engines;
  @Mock private FeeProperties properties;
  @Mock private PaymentGatewayRegistry gatewayRegistry;
  @Mock private FeeCollectionService feeCollections;
  @Mock private StudentProfileClient studentProfiles;
  @Mock private StudentFeeAdjustmentService feeAdjustments;

  private FinanceService service;

  @BeforeEach
  void setUp() {
    service =
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
    TenantContext.set(new TenantScope("SCH-A", "main", "2026-27", "accounts", "ACCOUNTANT"));
    when(engines.isFeatureEnabled(any(), any())).thenReturn(true);
    when(engines.getModuleSettings(any(), any()))
        .thenReturn(
            Map.of(
                "settings",
                Map.of(
                    "gstEnabled", false,
                    "lateFeeEnabled", true,
                    "includeHostelInDemand", false,
                    "includeTransportInDemand", false,
                    "autoApplyScholarship", false)));
    when(definitionRepo.existsByOrganizationIdAndDefinitionType(any(), any())).thenReturn(true);
    when(definitionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    FinanceDefinitionEntity seeded = new FinanceDefinitionEntity();
    seeded.setPayload(Map.of("definitionKey", "seed", "frequency", "Y", "ANNUAL", "1"));
    when(definitionRepo
            .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                any(), any(), any(), eq("ACTIVE")))
        .thenReturn(Optional.of(seeded));
    when(definitionRepo.findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
            any(), any(), eq("ACTIVE")))
        .thenReturn(List.of());
    when(studentProfiles.opsByAdmissionNo(any(), any())).thenReturn(Map.of());
    when(studentProfiles.byAdmissionNo(any(), any())).thenReturn(Map.of());
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void lateFeeIsANewLineAndTheTuitionAmountStays() {
    stubDefinition(
        FinanceCatalog.TYPE_FEE_STRUCTURE,
        "grade7",
        Map.of(
            "definitionKey", "grade7",
            "name", "Grade 7",
            "lines", List.of(Map.of("headKey", "TUITION", "amount", "10000", "frequency", "Y"))));
    stubDefinition(
        FinanceCatalog.TYPE_LATE_FEE_POLICY,
        "late_per_day_10",
        Map.of("definitionKey", "late_per_day_10", "enabled", true, "perDayAmount", "10", "graceDays", 0));

    Map<String, Object> demand =
        service.previewDemand(
            Map.of(
                "structureKey", "grade7",
                "studentRef", "ADM-1",
                "studentName", "Asha",
                "classSection", "Grade 7",
                "studentAnswers", Map.of(),
                "pendingDays", 2));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines = (List<Map<String, Object>>) demand.get("lines");
    assertEquals(0, new BigDecimal("10000").compareTo((BigDecimal) line(lines, "TUITION").get("amount")));
    assertEquals(new BigDecimal("20.00"), line(lines, "LATE_FEE").get("amount"));
    assertEquals("lateFee", line(lines, "LATE_FEE").get("source"));
  }

  @Test
  void oneInstallmentDoesNotSettleTheYear() {
    stubDefinition(
        FinanceCatalog.TYPE_FEE_STRUCTURE,
        "grade7",
        Map.of(
            "definitionKey", "grade7",
            "lines",
            List.of(
                Map.of(
                    "headKey", "TUITION",
                    "amount", "12000",
                    "frequency", "Y",
                    "portions",
                    List.of(
                        Map.of("key", "apr", "amount", "4000"),
                        Map.of("key", "aug", "amount", "4000"),
                        Map.of("key", "dec", "amount", "4000"))))));

    Map<String, Object> demand =
        service.previewDemand(
            Map.of(
                "structureKey", "grade7",
                "studentRef", "ADM-1",
                "studentName", "Asha",
                "classSection", "Grade 7",
                "studentAnswers", Map.of(),
                "portionKey", "apr",
                "paidPortionKeys", List.of("apr"),
                "pendingDays", 0));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines = (List<Map<String, Object>>) demand.get("lines");
    assertEquals(0, new BigDecimal("4000").compareTo((BigDecimal) line(lines, "TUITION").get("amount")));
    assertEquals(Boolean.FALSE, demand.get("yearSettled"));
  }

  @Test
  void refundPostsANewTransactionAgainstTheReceipt() {
    UUID collectionId = UUID.randomUUID();
    when(transactionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(feeCollections.get(collectionId)).thenReturn(Map.of("answers", Map.of("amount", "5000.00")));

    service.refund(
        Map.of(
            "amount", "500",
            "reason", "Overpaid",
            "headKey", "TUITION",
            "collectionId", collectionId.toString()));

    verify(feeCollections).recordRefund(eq(collectionId), eq(new BigDecimal("500")), eq("Overpaid"));
    ArgumentCaptor<com.sugamflow.school.fee.persistence.entity.FinanceTransactionEntity> saved =
        ArgumentCaptor.forClass(com.sugamflow.school.fee.persistence.entity.FinanceTransactionEntity.class);
    verify(transactionRepo).save(saved.capture());
    assertEquals("FEE_REFUND", saved.getValue().getTransactionType());
    assertEquals(collectionId, saved.getValue().getSourceCollectionId());
  }

  private void stubDefinition(String type, String key, Map<String, Object> payload) {
    FinanceDefinitionEntity entity = new FinanceDefinitionEntity();
    entity.setPayload(payload);
    when(definitionRepo
            .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                eq("SCH-A"), eq(type), eq(key), eq("ACTIVE")))
        .thenReturn(Optional.of(entity));
  }

  private static Map<String, Object> line(List<Map<String, Object>> lines, String head) {
    return lines.stream().filter(row -> head.equals(row.get("headKey"))).findFirst().orElseThrow();
  }
}
