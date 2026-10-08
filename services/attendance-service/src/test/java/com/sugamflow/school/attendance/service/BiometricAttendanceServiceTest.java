package com.sugamflow.school.attendance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.attendance.biometric.BiometricDeviceRejected;
import com.sugamflow.school.attendance.biometric.DeviceSecrets;
import com.sugamflow.school.attendance.integration.NotificationDeliveryClient;
import com.sugamflow.school.attendance.persistence.entity.AttendanceDeviceEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceMarkEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceSessionEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricDayEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricEnrollmentEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricEventEntity;
import com.sugamflow.school.attendance.persistence.repo.AttendanceCorrectionRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceDeviceRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceMarkRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceSessionRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricAuditRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricCommandRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricDayRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricEnrollmentRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricEventRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricRuleRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BiometricAttendanceServiceTest {

  @Mock private AttendanceDeviceRepository devices;
  @Mock private BiometricRuleRepository rules;
  @Mock private BiometricEnrollmentRepository enrollments;
  @Mock private BiometricEventRepository events;
  @Mock private BiometricDayRepository days;
  @Mock private BiometricCommandRepository commands;
  @Mock private BiometricAuditRepository audits;
  @Mock private AttendanceCorrectionRepository corrections;
  @Mock private AttendanceSessionRepository sessions;
  @Mock private AttendanceMarkRepository marks;
  @Mock private StaffAttendanceService staffAttendance;
  @Mock private NotificationDeliveryClient notifications;

  private BiometricAttendanceService service;
  private AttendanceDeviceEntity device;

  @BeforeEach
  void setUp() {
    service =
        new BiometricAttendanceService(
            devices,
            rules,
            enrollments,
            events,
            days,
            commands,
            audits,
            corrections,
            sessions,
            marks,
            staffAttendance,
            notifications);
    device = new AttendanceDeviceEntity();
    device.setId("dev-1");
    device.setOrganizationId("school-a");
    device.setBranchId("main");
    device.setName("Main Gate");
    device.setGateName("Main Gate");
    device.setSerialNumber("SN123");
    device.setStatus("ACTIVE");
    device.setTimeZone("Asia/Kolkata");
    device.setDirection("BOTH");
    device.setCredentialHash(DeviceSecrets.hash("secret-key"));
  }

  @Test
  void rejectsUnknownSerial() {
    when(devices.findBySerialNumber("NOPE")).thenReturn(Optional.empty());
    assertThrows(BiometricDeviceRejected.class, () -> service.authenticate("NOPE", "secret-key"));
  }

  @Test
  void rejectsBadKeyAndCountsTheError() {
    when(devices.findBySerialNumber("SN123")).thenReturn(Optional.of(device));
    when(devices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(audits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    assertThrows(BiometricDeviceRejected.class, () -> service.authenticate("SN123", "wrong"));
    assertEquals(1, device.getErrorCount());
  }

  @Test
  void checkInWritesRosterAndNotifiesOnce() {
    when(devices.findBySerialNumber("SN123")).thenReturn(Optional.of(device));
    when(rules.findByOrganizationIdAndBranchId("school-a", "main")).thenReturn(Optional.empty());
    when(rules.findByOrganizationIdAndBranchId("school-a", "")).thenReturn(Optional.empty());
    BiometricEnrollmentEntity enrollment = new BiometricEnrollmentEntity();
    enrollment.setOrganizationId("school-a");
    enrollment.setPersonType("STUDENT");
    enrollment.setPersonId(UUID.randomUUID().toString());
    enrollment.setPersonCode("ADM-1");
    enrollment.setDisplayName("Rahul Sharma");
    enrollment.setClassSection("8-A");
    enrollment.setSectionId(UUID.randomUUID());
    enrollment.setEnrollmentCode("10045");
    enrollment.setStatus("ACTIVE");
    enrollment.setNotifyMobile("9000000000");
    when(enrollments.findByOrganizationIdAndEnrollmentCode("school-a", "10045"))
        .thenReturn(Optional.of(enrollment));
    when(events.findByEventHash(any())).thenReturn(Optional.empty());
    when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(days.findByOrganizationIdAndPersonCodeAndAttendanceDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(days.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(sessions.findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodIdIsNull(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(marks.findBySessionIdAndStudentId(any(), any())).thenReturn(Optional.empty());
    when(marks.findBySessionIdAndAdmissionNo(any(), any())).thenReturn(Optional.empty());
    when(marks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(devices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(notifications.queue(any(), any(), any(), any(), any(), any()))
        .thenReturn(Map.of("status", "QUEUED"));

    AttendanceDeviceEntity authed = service.authenticate("SN123", "secret-key");
    service.ingestAttlog(authed, "10045\t2026-10-08 08:41:21\t0\t15\n");

    ArgumentCaptor<BiometricEventEntity> saved = ArgumentCaptor.forClass(BiometricEventEntity.class);
    verify(events).save(saved.capture());
    assertEquals("PROCESSED", saved.getValue().getStatus());
    assertEquals("CHECK_IN", saved.getValue().getEventType());
    assertTrue(saved.getValue().isNotified());
    verify(notifications).queue(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<AttendanceMarkEntity> mark = ArgumentCaptor.forClass(AttendanceMarkEntity.class);
    verify(marks).save(mark.capture());
    assertEquals("LATE", mark.getValue().getStatus());
  }

  @Test
  void duplicatePunchDoesNotNotifyAgain() {
    BiometricEventEntity prior = new BiometricEventEntity();
    prior.setId(UUID.randomUUID());
    prior.setStatus("PROCESSED");
    prior.setNotified(true);
    prior.setEventType("CHECK_IN");
    when(events.findByEventHash(any())).thenReturn(Optional.of(prior));
    when(rules.findByOrganizationIdAndBranchId(any(), any())).thenReturn(Optional.empty());
    when(days.findByOrganizationIdAndPersonCodeAndAttendanceDate(any(), any(), any()))
        .thenReturn(Optional.empty());

    int accepted = service.ingestAttlog(device, "10045\t2026-10-08 08:32:21\t0\t15\n");
    assertEquals(1, accepted);
    verify(notifications, never()).queue(any(), any(), any(), any(), any(), any());
    verify(days, never()).save(any());
  }

  @Test
  void unknownPersonIsStored() {
    when(rules.findByOrganizationIdAndBranchId(any(), any())).thenReturn(Optional.empty());
    when(days.findByOrganizationIdAndPersonCodeAndAttendanceDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(enrollments.findByOrganizationIdAndEnrollmentCode("school-a", "99999")).thenReturn(Optional.empty());
    when(events.findByEventHash(any())).thenReturn(Optional.empty());
    when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(devices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.ingestAttlog(device, "99999\t2026-10-08 08:32:21\t0\t1\n");

    ArgumentCaptor<BiometricEventEntity> saved = ArgumentCaptor.forClass(BiometricEventEntity.class);
    verify(events).save(saved.capture());
    assertEquals("UNKNOWN_PERSON", saved.getValue().getStatus());
    verify(days, never()).save(any(BiometricDayEntity.class));
  }

  @Test
  void enrollmentFromAnotherSchoolIsNotUsed() {
    when(rules.findByOrganizationIdAndBranchId(any(), any())).thenReturn(Optional.empty());
    when(enrollments.findByOrganizationIdAndEnrollmentCode("school-a", "10045")).thenReturn(Optional.empty());
    when(events.findByEventHash(any())).thenReturn(Optional.empty());
    when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(devices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.ingestNormalized(
        device,
        Map.of(
            "personCode", "10045",
            "eventTime", "2026-10-08T08:32:21+05:30",
            "eventType", "CHECK_IN",
            "verificationType", "FACE"));

    ArgumentCaptor<BiometricEventEntity> saved = ArgumentCaptor.forClass(BiometricEventEntity.class);
    verify(events).save(saved.capture());
    assertEquals("school-a", saved.getValue().getOrganizationId());
    assertEquals("UNKNOWN_PERSON", saved.getValue().getStatus());
  }
}
