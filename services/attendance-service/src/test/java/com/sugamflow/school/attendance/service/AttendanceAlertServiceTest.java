package com.sugamflow.school.attendance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.attendance.integration.ConfigEngineClient;
import com.sugamflow.school.attendance.integration.NotificationDeliveryClient;
import com.sugamflow.school.attendance.integration.StudentRecordClient;
import com.sugamflow.school.attendance.persistence.entity.AttendanceAlertOutboxEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceMarkEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceSessionEntity;
import com.sugamflow.school.attendance.persistence.repo.AttendanceAlertOutboxRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceMarkRepository;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.time.LocalDate;
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
class AttendanceAlertServiceTest {

  @Mock private AttendanceMarkRepository marks;
  @Mock private AttendanceAlertOutboxRepository outbox;
  @Mock private ConfigEngineClient engines;
  @Mock private NotificationDeliveryClient delivery;
  @Mock private StudentRecordClient studentRecords;
  @InjectMocks private AttendanceAlertService service;

  private final TenantScope scope =
      new TenantScope("demo-school", "main", "2025-26", "teacher", "TEACHER");

  private AttendanceSessionEntity session(UUID id) {
    AttendanceSessionEntity s = new AttendanceSessionEntity();
    s.setId(id);
    s.setOrganizationId("demo-school");
    s.setSectionId(UUID.randomUUID());
    s.setAttendanceDate(LocalDate.of(2026, 7, 17));
    s.setStatus("SUBMITTED");
    return s;
  }

  private AttendanceMarkEntity mark(UUID sessionId, String status, UUID studentId) {
    AttendanceMarkEntity m = new AttendanceMarkEntity();
    m.setId(UUID.randomUUID());
    m.setOrganizationId("demo-school");
    m.setSessionId(sessionId);
    m.setStudentId(studentId);
    m.setAdmissionNo("ADM-1");
    m.setStudentName("Asha");
    m.setStatus(status);
    return m;
  }

  private AttendanceAlertOutboxEntity outboxRow(
      AttendanceMarkEntity mark, String channel, String recipient, String status) {
    AttendanceAlertOutboxEntity e = new AttendanceAlertOutboxEntity();
    e.setId(UUID.randomUUID());
    e.setOrganizationId("demo-school");
    e.setSessionId(mark.getSessionId());
    e.setMarkId(mark.getId());
    e.setMarkStatus(mark.getStatus());
    e.setIntent("ATTENDANCE_" + mark.getStatus());
    e.setChannel(channel);
    e.setRecipient(recipient);
    e.setSubject("Absence alert");
    e.setBody("Asha absent");
    e.setStatus(status);
    return e;
  }

  private void stubGuardian(UUID studentId) {
    when(studentRecords.getStudent(any(), eq(studentId.toString())))
        .thenReturn(
            Map.of(
                "guardians",
                List.of(
                    Map.of(
                        "fullName", "Mrs Parent",
                        "mobile", "9800000001",
                        "email", "parent@x.com",
                        "isPrimary", true))));
  }

  @Test
  void notifiesGuardianForAbsentMarkAndPersistsOutboxRows() {
    UUID sessionId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    AttendanceSessionEntity session = session(sessionId);
    AttendanceMarkEntity absent = mark(sessionId, "ABSENT", studentId);
    AttendanceMarkEntity present = mark(sessionId, "PRESENT", UUID.randomUUID());

    when(engines.getModuleSettings(any(), eq("attendance"))).thenReturn(Map.of());
    when(marks.findBySessionIdOrderByStudentNameAsc(sessionId))
        .thenReturn(List.of(absent, present));
    stubGuardian(studentId);
    when(engines.resolveNotification(any(), eq("ATTENDANCE"), eq("ATTENDANCE_ABSENT"), any()))
        .thenReturn(Map.of("resolved", true, "subject", "Absence alert", "body", "Asha absent"));
    when(outbox.findByMarkIdAndMarkStatus(absent.getId(), "ABSENT")).thenReturn(List.of());
    when(outbox.findByMarkIdAndMarkStatusAndChannelAndRecipient(any(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(outbox.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(delivery.queue(
            eq("demo-school"), anyString(), anyString(), anyString(), anyString(), startsWith("att-alert-")))
        .thenReturn(Map.of("status", "SENT", "id", "n-1"));
    when(marks.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> summary = service.onSessionSubmitted(scope, session, "Grade 8-A");

    assertEquals(1, summary.get("notified"));
    // SMS + EMAIL both queued for the one absent student, each with a row-scoped idempotency key.
    verify(delivery)
        .queue(
            eq("demo-school"), eq("SMS"), eq("9800000001"), eq("Absence alert"), eq("Asha absent"),
            startsWith("att-alert-"));
    verify(delivery)
        .queue(
            eq("demo-school"), eq("EMAIL"), eq("parent@x.com"), eq("Absence alert"), eq("Asha absent"),
            startsWith("att-alert-"));
    assertEquals("ABSENT", absent.getLastNotifiedStatus());
  }

  @Test
  void skipsWhenAllOutboxRowsAlreadySent() {
    UUID sessionId = UUID.randomUUID();
    AttendanceSessionEntity session = session(sessionId);
    AttendanceMarkEntity absent = mark(sessionId, "ABSENT", UUID.randomUUID());

    when(engines.getModuleSettings(any(), eq("attendance"))).thenReturn(Map.of());
    when(marks.findBySessionIdOrderByStudentNameAsc(sessionId)).thenReturn(List.of(absent));
    when(outbox.findByMarkIdAndMarkStatus(absent.getId(), "ABSENT"))
        .thenReturn(
            List.of(
                outboxRow(absent, "SMS", "9800000001", AttendanceAlertOutboxEntity.STATUS_SENT),
                outboxRow(absent, "EMAIL", "parent@x.com", AttendanceAlertOutboxEntity.STATUS_SENT)));

    Map<String, Object> summary = service.onSessionSubmitted(scope, session, "Grade 8-A");

    assertEquals(0, summary.get("notified"));
    assertEquals(1, summary.get("skipped"));
    verify(delivery, never())
        .queue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void retriesOnlyFailedChannelOnResubmit() {
    UUID sessionId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    AttendanceSessionEntity session = session(sessionId);
    AttendanceMarkEntity absent = mark(sessionId, "ABSENT", studentId);

    AttendanceAlertOutboxEntity sentEmail =
        outboxRow(absent, "EMAIL", "parent@x.com", AttendanceAlertOutboxEntity.STATUS_SENT);
    AttendanceAlertOutboxEntity failedSms =
        outboxRow(absent, "SMS", "9800000001", AttendanceAlertOutboxEntity.STATUS_FAILED);

    when(engines.getModuleSettings(any(), eq("attendance"))).thenReturn(Map.of());
    when(marks.findBySessionIdOrderByStudentNameAsc(sessionId)).thenReturn(List.of(absent));
    stubGuardian(studentId);
    when(engines.resolveNotification(any(), eq("ATTENDANCE"), eq("ATTENDANCE_ABSENT"), any()))
        .thenReturn(Map.of("resolved", true, "subject", "Absence alert", "body", "Asha absent"));
    when(outbox.findByMarkIdAndMarkStatus(absent.getId(), "ABSENT"))
        .thenReturn(List.of(sentEmail, failedSms));
    when(outbox.findByMarkIdAndMarkStatusAndChannelAndRecipient(
            absent.getId(), "ABSENT", "SMS", "9800000001"))
        .thenReturn(Optional.of(failedSms));
    when(outbox.findByMarkIdAndMarkStatusAndChannelAndRecipient(
            absent.getId(), "ABSENT", "EMAIL", "parent@x.com"))
        .thenReturn(Optional.of(sentEmail));
    when(outbox.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(delivery.queue(
            eq("demo-school"), eq("SMS"), eq("9800000001"), anyString(), anyString(), anyString()))
        .thenReturn(Map.of("status", "SENT", "id", "n-3"));
    when(marks.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> summary = service.onSessionSubmitted(scope, session, "Grade 8-A");

    assertEquals(1, summary.get("notified"));
    // Only the failed SMS is re-dispatched; the already-sent EMAIL is left alone.
    verify(delivery)
        .queue(eq("demo-school"), eq("SMS"), eq("9800000001"), anyString(), anyString(), anyString());
    verify(delivery, never())
        .queue(eq("demo-school"), eq("EMAIL"), anyString(), anyString(), anyString(), anyString());
    assertEquals(AttendanceAlertOutboxEntity.STATUS_SENT, failedSms.getStatus());
    assertEquals(1, failedSms.getAttempts());
  }

  @Test
  void disabledByModuleSetting() {
    UUID sessionId = UUID.randomUUID();
    when(engines.getModuleSettings(any(), eq("attendance")))
        .thenReturn(Map.of("settings", Map.of("notifyOnRosterSubmit", false)));

    Map<String, Object> summary =
        service.onSessionSubmitted(scope, session(sessionId), "Grade 8-A");

    assertEquals(Boolean.TRUE, summary.get("disabled"));
    verify(marks, never()).findBySessionIdOrderByStudentNameAsc(any());
  }

  @Test
  void skipsWhenNoGuardianContact() {
    UUID sessionId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    AttendanceSessionEntity session = session(sessionId);
    AttendanceMarkEntity late = mark(sessionId, "LATE", studentId);

    when(engines.getModuleSettings(any(), eq("attendance"))).thenReturn(Map.of());
    when(marks.findBySessionIdOrderByStudentNameAsc(sessionId)).thenReturn(List.of(late));
    when(outbox.findByMarkIdAndMarkStatus(late.getId(), "LATE")).thenReturn(List.of());
    when(studentRecords.getStudent(any(), eq(studentId.toString()))).thenReturn(Map.of());

    Map<String, Object> summary = service.onSessionSubmitted(scope, session, "Grade 8-A");

    assertEquals(0, summary.get("notified"));
    assertEquals(1, summary.get("skipped"));
    verify(delivery, never())
        .queue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void mergesSplitGuardianContactsAcrossChannels() {
    UUID sessionId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    AttendanceSessionEntity session = session(sessionId);
    AttendanceMarkEntity absent = mark(sessionId, "ABSENT", studentId);

    when(engines.getModuleSettings(any(), eq("attendance"))).thenReturn(Map.of());
    when(marks.findBySessionIdOrderByStudentNameAsc(sessionId)).thenReturn(List.of(absent));
    when(studentRecords.getStudent(any(), eq(studentId.toString())))
        .thenReturn(
            Map.of(
                "guardians",
                List.of(
                    Map.of(
                        "fullName", "Primary Parent",
                        "mobile", "9800000001",
                        "isPrimary", true),
                    Map.of(
                        "fullName", "Secondary Parent",
                        "email", "second@x.com",
                        "isPrimary", false))));
    when(engines.resolveNotification(any(), eq("ATTENDANCE"), eq("ATTENDANCE_ABSENT"), any()))
        .thenReturn(Map.of("resolved", true, "subject", "Absence alert", "body", "Asha absent"));
    when(outbox.findByMarkIdAndMarkStatus(absent.getId(), "ABSENT")).thenReturn(List.of());
    when(outbox.findByMarkIdAndMarkStatusAndChannelAndRecipient(any(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(outbox.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(delivery.queue(eq("demo-school"), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Map.of("status", "SENT", "id", "n-2"));
    when(marks.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> summary = service.onSessionSubmitted(scope, session, "Grade 8-A");

    assertEquals(1, summary.get("notified"));
    verify(delivery)
        .queue(eq("demo-school"), eq("SMS"), eq("9800000001"), eq("Absence alert"), eq("Asha absent"), anyString());
    verify(delivery)
        .queue(eq("demo-school"), eq("EMAIL"), eq("second@x.com"), eq("Absence alert"), eq("Asha absent"), anyString());
  }

  @Test
  void sendsInAppAlertToEveryLinkedParentAccount() {
    UUID sessionId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    AttendanceSessionEntity session = session(sessionId);
    AttendanceMarkEntity absent = mark(sessionId, "ABSENT", studentId);

    when(engines.getModuleSettings(any(), eq("attendance")))
        .thenReturn(Map.of("settings", Map.of("rosterNotificationChannels", List.of("IN_APP"))));
    when(marks.findBySessionIdOrderByStudentNameAsc(sessionId)).thenReturn(List.of(absent));
    when(studentRecords.getStudent(any(), eq(studentId.toString())))
        .thenReturn(
            Map.of(
                "guardians",
                List.of(
                    Map.of("fullName", "Parent One", "authUsername", "parent1_demo-school"),
                    Map.of("fullName", "Parent Two", "username", "parent2_demo-school"))));
    when(engines.resolveNotification(any(), eq("ATTENDANCE"), eq("ATTENDANCE_ABSENT"), any()))
        .thenReturn(Map.of("resolved", true, "subject", "Absence alert", "body", "Asha absent"));
    when(outbox.findByMarkIdAndMarkStatus(absent.getId(), "ABSENT")).thenReturn(List.of());
    when(outbox.findByMarkIdAndMarkStatusAndChannelAndRecipient(
            any(), anyString(), eq("IN_APP"), anyString()))
        .thenReturn(Optional.empty());
    when(outbox.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(delivery.queue(
            eq("demo-school"), eq("IN_APP"), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Map.of("status", "SENT", "id", "in-app"));
    when(marks.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> summary = service.onSessionSubmitted(scope, session, "Grade 8-A");

    assertEquals(1, summary.get("notified"));
    verify(delivery)
        .queue(
            eq("demo-school"), eq("IN_APP"), eq("parent1_demo-school"),
            eq("Absence alert"), eq("Asha absent"), startsWith("att-alert-"));
    verify(delivery)
        .queue(
            eq("demo-school"), eq("IN_APP"), eq("parent2_demo-school"),
            eq("Absence alert"), eq("Asha absent"), startsWith("att-alert-"));
  }

  @Test
  void retryPendingRedispatchesRetryableRows() {
    AttendanceMarkEntity absent = mark(UUID.randomUUID(), "ABSENT", UUID.randomUUID());
    AttendanceAlertOutboxEntity failed =
        outboxRow(absent, "SMS", "9800000001", AttendanceAlertOutboxEntity.STATUS_FAILED);
    failed.setAttempts(2);
    failed.setCreatedAt(Instant.now());

    when(outbox.findRetryable(eq(5), any(), any())).thenReturn(List.of(failed));
    when(outbox.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(delivery.queue(
            eq("demo-school"), eq("SMS"), eq("9800000001"), anyString(), anyString(),
            eq("att-alert-" + failed.getId())))
        .thenReturn(Map.of("status", "SENT", "id", "n-4"));

    int sent = service.retryPending();

    assertEquals(1, sent);
    assertEquals(AttendanceAlertOutboxEntity.STATUS_SENT, failed.getStatus());
    assertEquals(3, failed.getAttempts());
  }
}
