package com.sugamflow.school.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.notification.integration.NotificationDeliveryClient;
import com.sugamflow.school.notification.integration.StudentGuardianClient;
import com.sugamflow.school.notification.persistence.entity.CommsAlertOutboxEntity;
import com.sugamflow.school.notification.persistence.entity.CommsAnnouncementEntity;
import com.sugamflow.school.notification.persistence.repo.CommsAlertOutboxRepository;
import com.sugamflow.school.notification.persistence.repo.CommsAnnouncementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommsFanOutServiceTest {

  @Mock private CommsAnnouncementRepository announcements;
  @Mock private CommsAlertOutboxRepository outbox;
  @Mock private StudentGuardianClient guardians;
  @Mock private NotificationDeliveryClient delivery;
  @InjectMocks private CommsFanOutService service;

  @Test
  void fansOutInAppAndEmailToLinkedGuardians() {
    TenantScope scope = new TenantScope("demo-school", "main", "2025-26", "admin", "ADMIN");
    CommsAnnouncementEntity announcement = new CommsAnnouncementEntity();
    announcement.setId(UUID.randomUUID());
    announcement.setOrganizationId("demo-school");
    announcement.setBranchId("main");
    announcement.setTitle("Holiday notice");
    announcement.setBody("School closed tomorrow");
    announcement.setChannel("EMAIL");
    announcement.setAudience("PARENTS");
    announcement.setStatus("QUEUED");
    announcement.setCreatedAt(Instant.now());
    announcement.setUpdatedAt(Instant.now());

    when(announcements.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(guardians.deliveryTargets(scope))
        .thenReturn(
            List.of(
                Map.of(
                    "identity", "parent1_demo-school",
                    "fullName", "Parent One",
                    "email", "one@x.com",
                    "mobile", "9800000001")));
    when(outbox.findByAnnouncementIdAndChannelAndRecipient(any(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(outbox.save(any())).thenAnswer(inv -> inv.getArgument(0));
    List<CommsAlertOutboxEntity> rows =
        List.of(
            savedRow(announcement, "IN_APP", "parent1_demo-school"),
            savedRow(announcement, "EMAIL", "one@x.com"));
    when(outbox.findByAnnouncementIdOrderByCreatedAtAsc(announcement.getId())).thenReturn(rows);
    when(delivery.queue(eq("demo-school"), anyString(), anyString(), anyString(), anyString(), startsWith("comms-ann-")))
        .thenReturn(Map.of("status", "SENT", "id", "n-1"));

    Map<String, Object> summary = service.fanOut(scope, announcement);

    assertEquals("SENT", announcement.getStatus());
    assertEquals(2, summary.get("sent"));
    verify(delivery)
        .queue(
            eq("demo-school"),
            eq("IN_APP"),
            eq("parent1_demo-school"),
            eq("Holiday notice"),
            eq("School closed tomorrow"),
            startsWith("comms-ann-"));
    verify(delivery)
        .queue(
            eq("demo-school"),
            eq("EMAIL"),
            eq("one@x.com"),
            eq("Holiday notice"),
            eq("School closed tomorrow"),
            startsWith("comms-ann-"));
  }

  private CommsAlertOutboxEntity savedRow(
      CommsAnnouncementEntity announcement, String channel, String recipient) {
    CommsAlertOutboxEntity row = new CommsAlertOutboxEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(announcement.getOrganizationId());
    row.setAnnouncementId(announcement.getId());
    row.setChannel(channel);
    row.setRecipient(recipient);
    row.setSubject(announcement.getTitle());
    row.setBody(announcement.getBody());
    row.setStatus(CommsAlertOutboxEntity.STATUS_PENDING);
    return row;
  }
}
