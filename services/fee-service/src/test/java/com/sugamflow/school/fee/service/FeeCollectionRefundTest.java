package com.sugamflow.school.fee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.config.FeeProperties;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.integration.NotificationDeliveryClient;
import com.sugamflow.school.fee.integration.StudentAccessClient;
import com.sugamflow.school.fee.integration.StudentProfileClient;
import com.sugamflow.school.fee.persistence.entity.FeeCollectionEntity;
import com.sugamflow.school.fee.persistence.repo.FeeCollectionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCollectionRefundTest {

  @Mock private FeeCollectionRepository repository;
  @Mock private ConfigEngineClient engines;
  @Mock private NotificationDeliveryClient notificationDelivery;
  @Mock private FinanceService financeService;
  @Mock private StudentAccessClient studentAccess;
  @Mock private StudentProfileClient studentProfile;

  private FeeCollectionService service;

  @BeforeEach
  void setUp() {
    service =
        new FeeCollectionService(
            repository,
            engines,
            notificationDelivery,
            new FeeProperties(),
            financeService,
            studentAccess,
            studentProfile);
    TenantContext.set(new TenantScope("SCH-A", "main", "2026-27", "accounts", "ACCOUNTANT"));
    when(engines.isFeatureEnabled(any(), eq(FeeCollectionService.FEATURE_FEE))).thenReturn(true);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void refundLowersTheBalanceAndLeavesTheReceiptAmount() {
    UUID id = UUID.randomUUID();
    FeeCollectionEntity entity = new FeeCollectionEntity();
    entity.setId(id);
    entity.setOrganizationId("SCH-A");
    entity.setFormKey("fee");
    entity.setWorkflowKey("fee");
    entity.setStatus("APPROVED");
    entity.setAnswers(
        Map.of(
            "amount", "5000.00",
            "remaining", "5000.00",
            "feeLines", List.of(Map.of("headKey", "TUITION", "amount", "5000.00"))));
    when(repository.findByIdAndOrganizationId(id, "SCH-A")).thenReturn(Optional.of(entity));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Map<String, Object> dto = service.recordRefund(id, new BigDecimal("500"), "Overpaid");

    @SuppressWarnings("unchecked")
    Map<String, Object> answers = (Map<String, Object>) dto.get("answers");
    assertEquals("5000.00", answers.get("amount"));
    assertEquals("4500.00", answers.get("remaining"));
    assertEquals("500.00", answers.get("refundAmount"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines = (List<Map<String, Object>>) answers.get("feeLines");
    assertEquals("5000.00", lines.get(0).get("amount"));
  }
}
