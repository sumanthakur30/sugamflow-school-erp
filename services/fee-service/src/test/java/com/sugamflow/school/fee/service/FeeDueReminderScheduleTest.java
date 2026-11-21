package com.sugamflow.school.fee.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.integration.NotificationDeliveryClient;
import com.sugamflow.school.fee.integration.StudentProfileClient;
import com.sugamflow.school.fee.persistence.entity.FeeCollectionEntity;
import com.sugamflow.school.fee.persistence.repo.FeeCollectionRepository;
import com.sugamflow.school.fee.persistence.repo.FeeDueReminderOutboxRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeDueReminderScheduleTest {

  @Mock private FeeCollectionRepository collections;
  @Mock private FeeDueReminderOutboxRepository outbox;
  @Mock private StudentProfileClient students;
  @Mock private NotificationDeliveryClient delivery;
  @Mock private ConfigEngineClient engines;

  private FeeDueReminderService service;

  @BeforeEach
  void setUp() {
    TenantContext.set(new TenantScope("org", "campus-a", "2026", "user", "ADMIN"));
    service = new FeeDueReminderService(collections, outbox, students, delivery, engines);
    when(engines.getModuleSettings(any(), eq("fee"))).thenReturn(Map.of("notifyOnDue", true));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void aScheduleSendsNothingUntilTheSchoolTurnsItOn() {
    Map<String, Object> result = service.run(Map.of("scheduled", true));

    assertEquals(false, result.get("scheduled"));
    assertEquals(0, result.get("sent"));
    verify(collections, never())
        .findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(any(), any(), any());
    verifyNoInteractions(delivery);
  }

  @Test
  void theOfficeSeesADraftWithoutSendingIt() {
    FeeCollectionEntity due = new FeeCollectionEntity();
    due.setId(UUID.randomUUID());
    due.setStatus("IN_PROGRESS");
    due.setAnswers(
        Map.of("admissionNo", "ADM-1", "amount", "500", "pendingDays", 3, "feeHead", "TUITION"));
    when(collections.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
            "org", "campus-a", "2026"))
        .thenReturn(List.of(due));

    List<Map<String, Object>> drafts = service.drafts("ADM-1");

    assertEquals(1, drafts.size());
    assertTrue(String.valueOf(drafts.get(0).get("message")).contains("ADM-1"));
    verifyNoInteractions(outbox);
    verifyNoInteractions(delivery);
  }
}
