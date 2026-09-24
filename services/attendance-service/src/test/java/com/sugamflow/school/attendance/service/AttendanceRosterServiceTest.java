package com.sugamflow.school.attendance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.attendance.integration.AcademicClient;
import com.sugamflow.school.attendance.integration.StudentAccessClient;
import com.sugamflow.school.attendance.integration.StudentDirectoryClient;
import com.sugamflow.school.attendance.persistence.entity.AttendanceMarkEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceSessionEntity;
import com.sugamflow.school.attendance.persistence.repo.AttendanceMarkRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceSessionRepository;
import com.sugamflow.school.attendance.web.AttendanceException;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceRosterServiceTest {

  @Mock private AttendanceSessionRepository sessions;
  @Mock private AttendanceMarkRepository marks;
  @Mock private StudentDirectoryClient directory;
  @Mock private AcademicClient academic;
  @Mock private StudentAccessClient studentAccess;
  @Mock private AttendanceAlertService alerts;
  @InjectMocks private AttendanceRosterService service;

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void rosterMergesDirectoryWithExistingMarks() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID sectionId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 17);

    when(academic.getSection(any(), eq(sectionId.toString())))
        .thenReturn(Map.of("studentLabel", "Grade 8-A", "name", "A"));

    AttendanceSessionEntity session = new AttendanceSessionEntity();
    session.setId(sessionId);
    session.setSectionId(sectionId);
    session.setAttendanceDate(date);
    session.setStatus("DRAFT");
    when(sessions.findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodIdIsNull(
            "demo-school", sectionId, date))
        .thenReturn(Optional.of(session));

    AttendanceMarkEntity mark = new AttendanceMarkEntity();
    mark.setStudentId(studentId);
    mark.setStatus("ABSENT");
    mark.setRemark("sick");
    when(marks.findBySessionIdOrderByStudentNameAsc(sessionId)).thenReturn(List.of(mark));

    when(directory.listByClassSection(any(), eq("Grade 8-A"), anyInt()))
        .thenReturn(
            List.of(
                Map.of(
                    "id", studentId.toString(),
                    "admissionNo", "ADM-1",
                    "fullName", "Asha",
                    "classSection", "Grade 8-A")));

    Map<String, Object> out = service.roster(sectionId, date, null);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> students = (List<Map<String, Object>>) out.get("students");
    assertEquals(1, students.size());
    assertEquals("ABSENT", students.get(0).get("markStatus"));
    assertEquals("Asha", students.get(0).get("studentName"));
  }

  @Test
  void bulkMarkRejectsLockedSession() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID sectionId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 17);

    AttendanceSessionEntity session = new AttendanceSessionEntity();
    session.setId(UUID.randomUUID());
    session.setSectionId(sectionId);
    session.setStatus("LOCKED");
    when(sessions.findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodIdIsNull(
            "demo-school", sectionId, date))
        .thenReturn(Optional.of(session));

    AttendanceException ex =
        assertThrows(
            AttendanceException.class,
            () ->
                service.bulkMark(
                    Map.of(
                        "sectionId",
                        sectionId.toString(),
                        "date",
                        date.toString(),
                        "marks",
                        List.of(
                            Map.of(
                                "admissionNo", "ADM-1",
                                "studentName", "Asha",
                                "status", "PRESENT")))));
    assertEquals("LOCKED", ex.getCode());
  }

  @Test
  void bulkMarkCreatesDraftAndUpserts() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "SHOP_OWNER"));
    UUID sectionId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 17);

    when(sessions.findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodIdIsNull(
            "demo-school", sectionId, date))
        .thenReturn(Optional.empty());
    when(sessions.save(any()))
        .thenAnswer(
            inv -> {
              AttendanceSessionEntity e = inv.getArgument(0);
              if (e.getId() == null) {
                e.setId(UUID.randomUUID());
              }
              return e;
            });
    when(marks.findBySessionIdAndAdmissionNo(any(), eq("ADM-1"))).thenReturn(Optional.empty());
    when(marks.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(marks.findBySessionIdOrderByStudentNameAsc(any())).thenReturn(List.of());

    Map<String, Object> out =
        service.bulkMark(
            Map.of(
                "sectionId",
                sectionId.toString(),
                "date",
                date.toString(),
                "marks",
                List.of(
                    Map.of(
                        "admissionNo", "ADM-1",
                        "studentName", "Asha",
                        "status", "PRESENT"))));

    @SuppressWarnings("unchecked")
    Map<String, Object> session = (Map<String, Object>) out.get("session");
    assertEquals("DRAFT", session.get("status"));

    ArgumentCaptor<AttendanceMarkEntity> cap = ArgumentCaptor.forClass(AttendanceMarkEntity.class);
    verify(marks).save(cap.capture());
    assertEquals("PRESENT", cap.getValue().getStatus());
    assertEquals("ADM-1", cap.getValue().getAdmissionNo());
  }
}
