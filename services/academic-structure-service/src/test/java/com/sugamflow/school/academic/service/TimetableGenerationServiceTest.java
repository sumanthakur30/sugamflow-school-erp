package com.sugamflow.school.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.sugamflow.school.academic.persistence.entity.ClassSectionEntity;
import com.sugamflow.school.academic.persistence.entity.TeachingAssignmentEntity;
import com.sugamflow.school.academic.persistence.entity.TimetablePeriodEntity;
import com.sugamflow.school.academic.persistence.entity.TimetableRoomEntity;
import com.sugamflow.school.academic.persistence.entity.TimetableSlotEntity;
import com.sugamflow.school.academic.persistence.repo.ClassSectionRepository;
import com.sugamflow.school.academic.persistence.repo.TeachingAssignmentRepository;
import com.sugamflow.school.academic.persistence.repo.TimetablePeriodRepository;
import com.sugamflow.school.academic.persistence.repo.TimetableRoomRepository;
import com.sugamflow.school.academic.persistence.repo.TimetableSlotRepository;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimetableGenerationServiceTest {

  @Mock private TimetablePeriodRepository periods;
  @Mock private TimetableSlotRepository slots;
  @Mock private ClassSectionRepository sections;
  @Mock private TeachingAssignmentRepository assignments;
  @Mock private TimetableRoomRepository rooms;
  @InjectMocks private TimetableGenerationService service;

  private final UUID sectionId = UUID.randomUUID();
  private final UUID periodId = UUID.randomUUID();
  private final UUID subjectId = UUID.randomUUID();

  @BeforeEach
  void setTenant() {
    TenantContext.set(new TenantScope("NAT-01", "main", "2025-26", "admin", "ADMIN"));
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void generationHonorsAvailabilityExistingTeacherLoadAndRoomCapacity() {
    ClassSectionEntity section = section(30);
    TimetablePeriodEntity period = period();
    TeachingAssignmentEntity assignment = assignment(2);
    assignment.setUnavailableSlots(
        List.of(Map.of("dayOfWeek", 1, "periodId", periodId.toString())));

    TimetableSlotEntity external = new TimetableSlotEntity();
    external.setOrganizationId("NAT-01");
    external.setBranchId("main");
    external.setAcademicSessionId("2025-26");
    external.setSectionId(UUID.randomUUID());
    external.setDayOfWeek(2);
    external.setPeriodId(periodId);
    external.setTeacherUsername("teacher1");

    TimetableRoomEntity room = new TimetableRoomEntity();
    room.setId(UUID.randomUUID());
    room.setOrganizationId("NAT-01");
    room.setBranchId("main");
    room.setAcademicSessionId("2025-26");
    room.setName("Room 101");
    room.setCapacity(40);

    when(sections.findByIdAndOrganizationId(sectionId, "NAT-01"))
        .thenReturn(Optional.of(section));
    when(periods.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByPeriodNoAsc(
            "NAT-01", "main", "2025-26"))
        .thenReturn(List.of(period));
    when(assignments.findByOrganizationIdAndSectionId("NAT-01", sectionId))
        .thenReturn(List.of(assignment));
    when(slots.findByOrganizationId("NAT-01")).thenReturn(List.of(external));
    when(rooms.findByOrganizationIdOrderByNameAsc("NAT-01")).thenReturn(List.of(room));

    Map<String, Object> result = service.generate(sectionId);

    assertTrue((Boolean) result.get("complete"));
    assertEquals(2, result.get("placedPeriods"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> generated = (List<Map<String, Object>>) result.get("slots");
    assertEquals(List.of(3, 4), generated.stream().map(slot -> slot.get("dayOfWeek")).toList());
    assertTrue(generated.stream().allMatch(slot -> "Room 101".equals(slot.get("room"))));
  }

  @Test
  void validationReportsTeacherCollision() {
    ClassSectionEntity section = section(20);
    TimetableSlotEntity external = new TimetableSlotEntity();
    external.setOrganizationId("NAT-01");
    external.setBranchId("main");
    external.setAcademicSessionId("2025-26");
    external.setSectionId(UUID.randomUUID());
    external.setDayOfWeek(1);
    external.setPeriodId(periodId);
    external.setTeacherUsername("teacher1");

    when(sections.findByIdAndOrganizationId(sectionId, "NAT-01"))
        .thenReturn(Optional.of(section));
    when(slots.findByOrganizationId("NAT-01")).thenReturn(List.of(external));
    when(assignments.findByOrganizationIdAndSectionId("NAT-01", sectionId))
        .thenReturn(List.of(assignment(1)));
    when(rooms.findByOrganizationIdOrderByNameAsc("NAT-01")).thenReturn(List.of());

    Map<String, Object> result =
        service.validate(
            sectionId,
            Map.of(
                "slots",
                List.of(
                    Map.of(
                        "dayOfWeek", 1,
                        "periodId", periodId.toString(),
                        "subjectId", subjectId.toString(),
                        "teacherUsername", "teacher1"))));

    assertFalse((Boolean) result.get("valid"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> conflicts = (List<Map<String, Object>>) result.get("conflicts");
    assertTrue(conflicts.stream().anyMatch(c -> "TEACHER_COLLISION".equals(c.get("code"))));
  }

  private ClassSectionEntity section(int capacity) {
    ClassSectionEntity section = new ClassSectionEntity();
    section.setId(sectionId);
    section.setOrganizationId("NAT-01");
    section.setBranchId("main");
    section.setAcademicSessionId("2025-26");
    section.setCapacity(capacity);
    section.setRoom(null);
    return section;
  }

  private TimetablePeriodEntity period() {
    TimetablePeriodEntity period = new TimetablePeriodEntity();
    period.setId(periodId);
    period.setOrganizationId("NAT-01");
    period.setBranchId("main");
    period.setAcademicSessionId("2025-26");
    period.setPeriodNo(1);
    period.setLabel("Period 1");
    period.setBreakPeriod(false);
    return period;
  }

  private TeachingAssignmentEntity assignment(int weeklyPeriods) {
    TeachingAssignmentEntity assignment = new TeachingAssignmentEntity();
    assignment.setId(UUID.randomUUID());
    assignment.setOrganizationId("NAT-01");
    assignment.setBranchId("main");
    assignment.setAcademicSessionId("2025-26");
    assignment.setSectionId(sectionId);
    assignment.setSubjectId(subjectId);
    assignment.setTeacherUsername("teacher1");
    assignment.setWeeklyPeriods(weeklyPeriods);
    assignment.setMaxDailyPeriods(1);
    assignment.setStatus("ACTIVE");
    return assignment;
  }
}
