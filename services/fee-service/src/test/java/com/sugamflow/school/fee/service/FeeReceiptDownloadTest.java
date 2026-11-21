package com.sugamflow.school.fee.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.sugamflow.school.fee.web.FeeException;
import java.util.Base64;
import java.util.LinkedHashMap;
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
class FeeReceiptDownloadTest {

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
    TenantContext.set(new TenantScope("SCH-A", "main", "2025-26", "clerk-a", "CLERK"));
    when(engines.isFeatureEnabled(any(), eq(FeeCollectionService.FEATURE_FEE))).thenReturn(true);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void approvedCollectionWithoutReceiptIsRenderedAndStored() {
    FeeCollectionEntity entity = collection("APPROVED");
    when(repository.findByIdAndOrganizationId(entity.getId(), "SCH-A"))
        .thenReturn(Optional.of(entity));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(engines.getModuleSettings(any(), eq(FeeCollectionService.MODULE_FEE))).thenReturn(Map.of());
    byte[] pdf = "receipt-pdf".getBytes();
    when(engines.renderReport(any(), eq("fee_receipt"), any()))
        .thenReturn(Map.of("contentBase64", Base64.getEncoder().encodeToString(pdf)));

    assertArrayEquals(pdf, service.getFeeReceiptPdf(entity.getId()));
    assertEquals(1, entity.getDocuments().size());
    assertEquals("READY", entity.getDocuments().get(0).get("status"));
    verify(repository).save(entity);
  }

  @Test
  void openCollectionDoesNotRenderAReceipt() {
    FeeCollectionEntity entity = collection("IN_PROGRESS");
    when(repository.findByIdAndOrganizationId(entity.getId(), "SCH-A"))
        .thenReturn(Optional.of(entity));

    FeeException missing =
        assertThrows(FeeException.class, () -> service.getFeeReceiptPdf(entity.getId()));
    assertEquals("FEE_RECEIPT_MISSING", missing.getCode());
    verify(engines, never()).renderReport(any(), any(), any());
    verify(repository, never()).save(any());
  }

  private static FeeCollectionEntity collection(String status) {
    FeeCollectionEntity entity = new FeeCollectionEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId("SCH-A");
    entity.setBranchId("main");
    entity.setAcademicSessionId("2025-26");
    entity.setFormKey("fee_collection");
    entity.setWorkflowKey("fee");
    entity.setStatus(status);
    entity.setAnswers(new LinkedHashMap<>(Map.of("admissionNo", "ADM-A", "amount", "500")));
    return entity;
  }
}
