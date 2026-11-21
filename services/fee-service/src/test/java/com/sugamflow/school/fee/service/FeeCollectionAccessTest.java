package com.sugamflow.school.fee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.security.AccessScope;
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
class FeeCollectionAccessTest {

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
    TenantContext.set(new TenantScope("SCH-A", "main", "2025-26", "parent-a", "PARENT"));
    when(engines.isFeatureEnabled(any(), eq(FeeCollectionService.FEATURE_FEE))).thenReturn(true);
    when(studentAccess.resolve(any()))
        .thenReturn(AccessScope.of("PARENT", List.of(), List.of("ADM-A"), List.of()));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void parentCannotReadAnotherParentsFees() {
    FeeCollectionEntity own = fee("ADM-A", "IN_PROGRESS", "1100");
    FeeCollectionEntity other = fee("ADM-B", "IN_PROGRESS", "2200");
    when(repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
            "SCH-A", "main", "2025-26"))
        .thenReturn(List.of(own, other));

    var page = service.list(0, 20);
    assertEquals(1, page.items().size());
    assertEquals("ADM-A", answers(page.items().get(0)).get("admissionNo"));

    List<Map<String, Object>> dues = service.defaulters();
    assertEquals(1, dues.size());
    assertEquals("ADM-A", dues.get(0).get("admissionNo"));

    when(repository.findByIdAndOrganizationId(other.getId(), "SCH-A")).thenReturn(Optional.of(other));
    FeeException hidden = assertThrows(FeeException.class, () -> service.get(other.getId()));
    assertEquals("NOT_FOUND", hidden.getCode());
    verifyNoInteractions(studentProfile);

    FeeException balance =
        assertThrows(FeeException.class, () -> service.billBalance("ADM-B", "tuition"));
    assertEquals("NOT_FOUND", balance.getCode());
    verify(repository, never()).sumApprovedForBill(anyString(), eq("ADM-B"), anyString());
    verify(repository, never())
        .findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
            eq("SCH-B"), anyString(), anyString());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> answers(Map<String, Object> dto) {
    return (Map<String, Object>) dto.get("answers");
  }

  private static FeeCollectionEntity fee(String admissionNo, String status, String amount) {
    FeeCollectionEntity entity = new FeeCollectionEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId("SCH-A");
    entity.setBranchId("main");
    entity.setAcademicSessionId("2025-26");
    entity.setFormKey("fee");
    entity.setWorkflowKey("fee");
    entity.setStatus(status);
    entity.setAnswers(
        Map.of(
            "admissionNo", admissionNo,
            "studentName", admissionNo,
            "amount", amount,
            "feeHead", "Tuition"));
    return entity;
  }
}
