package com.sugamflow.school.academic.service;

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
import com.sugamflow.school.academic.web.AcademicException;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic constraint-based timetable generator. The endpoint is described as AI-assisted in
 * the UI, but generation remains explainable: every placement is checked against teacher
 * availability, subject load, room capacity, teacher collisions, and room collisions.
 */
@Service
public class TimetableGenerationService {

  private final TimetablePeriodRepository periods;
  private final TimetableSlotRepository slots;
  private final ClassSectionRepository sections;
  private final TeachingAssignmentRepository assignments;
  private final TimetableRoomRepository rooms;

  public TimetableGenerationService(
      TimetablePeriodRepository periods,
      TimetableSlotRepository slots,
      ClassSectionRepository sections,
      TeachingAssignmentRepository assignments,
      TimetableRoomRepository rooms) {
    this.periods = periods;
    this.slots = slots;
    this.sections = sections;
    this.assignments = assignments;
    this.rooms = rooms;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listRooms() {
    TenantScope scope = TenantContext.require();
    return rooms.findByOrganizationIdOrderByNameAsc(scope.organizationId()).stream()
        .filter(room -> inScope(room.getBranchId(), room.getAcademicSessionId(), scope))
        .map(this::roomMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> saveRoom(Map<String, Object> body) {
    TenantScope scope = requireStaff();
    String name = RequestValues.str(body, "name");
    if (name == null) {
      throw AcademicException.badRequest("Room name is required");
    }
    UUID id = RequestValues.uuid(body, "id");
    TimetableRoomEntity room =
        id == null
            ? null
            : rooms.findByIdAndOrganizationId(id, scope.organizationId()).orElse(null);
    if (room == null) {
      room = new TimetableRoomEntity();
      room.setId(UUID.randomUUID());
      room.setOrganizationId(scope.organizationId());
      room.setBranchId(scope.branchId());
      room.setAcademicSessionId(scope.academicSessionId());
      room.setCreatedAt(Instant.now());
    }
    room.setName(name);
    room.setCapacity(Math.max(1, RequestValues.intOr(body, "capacity", room.getCapacity())));
    room.setStatus(RequestValues.strOr(body, "status", "ACTIVE"));
    room.setUpdatedAt(Instant.now());
    return roomMap(rooms.save(room));
  }

  @Transactional
  public void deleteRoom(UUID id) {
    TenantScope scope = requireStaff();
    TimetableRoomEntity room =
        rooms
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Room"));
    rooms.delete(room);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> generate(UUID sectionId) {
    TenantScope scope = requireStaff();
    ClassSectionEntity section = requireSection(sectionId, scope);
    List<TimetablePeriodEntity> teachingPeriods =
        scopedPeriods(scope).stream().filter(period -> !period.isBreakPeriod()).toList();
    List<TeachingAssignmentEntity> activeAssignments =
        assignments.findByOrganizationIdAndSectionId(scope.organizationId(), sectionId).stream()
            .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
            .filter(a -> a.getSubjectId() != null && hasText(a.getTeacherUsername()))
            .sorted(Comparator.comparing(a -> String.valueOf(a.getSubjectId())))
            .toList();

    List<Map<String, Object>> conflicts = new ArrayList<>();
    if (teachingPeriods.isEmpty()) {
      conflicts.add(conflict("NO_PERIODS", "Set up at least one teaching period first", "ERROR"));
    }
    if (activeAssignments.isEmpty()) {
      conflicts.add(
          conflict(
              "NO_ASSIGNMENTS",
              "Map subjects to teachers and set weekly loads before generating",
              "ERROR"));
    }
    if (!conflicts.isEmpty()) {
      return generationResult(List.of(), conflicts, 0, 0);
    }

    List<TimetableSlotEntity> external =
        slots.findByOrganizationId(scope.organizationId()).stream()
            .filter(slot -> !sectionId.equals(slot.getSectionId()))
            .filter(slot -> inScope(slot.getBranchId(), slot.getAcademicSessionId(), scope))
            .toList();
    Set<String> teacherBusy = new HashSet<>();
    Set<String> roomBusy = new HashSet<>();
    for (TimetableSlotEntity slot : external) {
      if (hasText(slot.getTeacherUsername())) {
        teacherBusy.add(teacherKey(slot.getTeacherUsername(), slot.getDayOfWeek(), slot.getPeriodId()));
      }
      if (hasText(slot.getRoom())) {
        roomBusy.add(roomKey(slot.getRoom(), slot.getDayOfWeek(), slot.getPeriodId()));
      }
    }

    List<TimetableRoomEntity> usableRooms =
        rooms.findByOrganizationIdOrderByNameAsc(scope.organizationId()).stream()
            .filter(room -> "ACTIVE".equalsIgnoreCase(room.getStatus()))
            .filter(room -> inScope(room.getBranchId(), room.getAcademicSessionId(), scope))
            .filter(room -> section.getCapacity() == null || room.getCapacity() >= section.getCapacity())
            .toList();

    List<Cell> cells = new ArrayList<>();
    for (int day = 1; day <= 6; day++) {
      for (TimetablePeriodEntity period : teachingPeriods) {
        cells.add(new Cell(day, period.getId(), period.getPeriodNo()));
      }
    }

    Set<String> usedCells = new HashSet<>();
    List<Map<String, Object>> generated = new ArrayList<>();
    int requested = 0;
    for (TeachingAssignmentEntity assignment : activeAssignments) {
      int weeklyLoad = Math.max(1, assignment.getWeeklyPeriods());
      requested += weeklyLoad;
      Map<Integer, Integer> subjectDaily = new HashMap<>();
      int placed = 0;
      while (placed < weeklyLoad) {
        Cell best =
            cells.stream()
                .filter(cell -> !usedCells.contains(cell.key()))
                .filter(
                    cell ->
                        subjectDaily.getOrDefault(cell.day(), 0)
                            < Math.max(1, assignment.getMaxDailyPeriods()))
                .filter(cell -> !isUnavailable(assignment, cell))
                .filter(
                    cell ->
                        !teacherBusy.contains(
                            teacherKey(
                                assignment.getTeacherUsername(), cell.day(), cell.periodId())))
                .sorted(
                    Comparator.comparingInt(
                            (Cell cell) -> subjectDaily.getOrDefault(cell.day(), 0))
                        .thenComparingInt(Cell::periodNo)
                        .thenComparingInt(Cell::day))
                .findFirst()
                .orElse(null);
        if (best == null) {
          break;
        }

        String room = chooseRoom(assignment, section, usableRooms, roomBusy, best);
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("dayOfWeek", best.day());
        slot.put("periodId", best.periodId().toString());
        slot.put("subjectId", assignment.getSubjectId().toString());
        slot.put("teacherUsername", assignment.getTeacherUsername());
        slot.put("room", room);
        slot.put("source", "AI_GENERATED");
        generated.add(slot);
        usedCells.add(best.key());
        teacherBusy.add(
            teacherKey(assignment.getTeacherUsername(), best.day(), best.periodId()));
        if (hasText(room)) {
          roomBusy.add(roomKey(room, best.day(), best.periodId()));
        }
        subjectDaily.merge(best.day(), 1, Integer::sum);
        placed++;
      }
      if (placed < weeklyLoad) {
        conflicts.add(
            conflict(
                "UNSCHEDULED_LOAD",
                (weeklyLoad - placed)
                    + " period(s) could not be placed for "
                    + assignment.getTeacherUsername()
                    + ". Review availability, daily limit, or existing conflicts.",
                "ERROR"));
      }
    }

    if (section.getCapacity() != null && usableRooms.isEmpty() && !hasText(section.getRoom())) {
      conflicts.add(
          conflict(
              "ROOM_CAPACITY",
              "No active room can hold the section capacity of " + section.getCapacity(),
              "WARNING"));
    }
    return generationResult(generated, conflicts, requested, generated.size());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> validate(UUID sectionId, Map<String, Object> body) {
    TenantScope scope = requireStaff();
    ClassSectionEntity section = requireSection(sectionId, scope);
    List<Map<String, Object>> proposed = slotMaps(body == null ? null : body.get("slots"));
    List<Map<String, Object>> conflicts = new ArrayList<>();
    Set<String> cells = new HashSet<>();
    Set<String> teachers = new HashSet<>();
    Set<String> roomCells = new HashSet<>();

    List<TimetableSlotEntity> external =
        slots.findByOrganizationId(scope.organizationId()).stream()
            .filter(slot -> !sectionId.equals(slot.getSectionId()))
            .filter(slot -> inScope(slot.getBranchId(), slot.getAcademicSessionId(), scope))
            .toList();
    Set<String> externalTeacherBusy = new HashSet<>();
    Set<String> externalRoomBusy = new HashSet<>();
    for (TimetableSlotEntity slot : external) {
      if (hasText(slot.getTeacherUsername())) {
        externalTeacherBusy.add(
            teacherKey(slot.getTeacherUsername(), slot.getDayOfWeek(), slot.getPeriodId()));
      }
      if (hasText(slot.getRoom())) {
        externalRoomBusy.add(roomKey(slot.getRoom(), slot.getDayOfWeek(), slot.getPeriodId()));
      }
    }

    Map<String, TeachingAssignmentEntity> bySubjectTeacher = new HashMap<>();
    for (TeachingAssignmentEntity assignment :
        assignments.findByOrganizationIdAndSectionId(scope.organizationId(), sectionId)) {
      bySubjectTeacher.put(
          assignment.getSubjectId() + "|" + lower(assignment.getTeacherUsername()), assignment);
    }
    Map<String, TimetableRoomEntity> roomByName = new HashMap<>();
    for (TimetableRoomEntity room : rooms.findByOrganizationIdOrderByNameAsc(scope.organizationId())) {
      roomByName.put(lower(room.getName()), room);
    }

    for (Map<String, Object> slot : proposed) {
      int day = intValue(slot.get("dayOfWeek"), 0);
      UUID periodId = uuid(slot.get("periodId"));
      String teacher = text(slot.get("teacherUsername"));
      String room = text(slot.get("room"));
      String subjectId = text(slot.get("subjectId"));
      if (day < 1 || day > 7 || periodId == null) {
        conflicts.add(conflict("INVALID_CELL", "Each slot needs a valid day and period", "ERROR"));
        continue;
      }
      String cell = day + "|" + periodId;
      if (!cells.add(cell)) {
        conflicts.add(conflict("SECTION_COLLISION", "The section has two lessons in the same period", "ERROR"));
      }
      if (hasText(teacher)) {
        String key = teacherKey(teacher, day, periodId);
        if (!teachers.add(key) || externalTeacherBusy.contains(key)) {
          conflicts.add(
              conflict(
                  "TEACHER_COLLISION",
                  teacher + " is already teaching in this period",
                  "ERROR"));
        }
        TeachingAssignmentEntity assignment =
            bySubjectTeacher.get(subjectId + "|" + lower(teacher));
        if (assignment != null && isUnavailable(assignment, new Cell(day, periodId, 0))) {
          conflicts.add(
              conflict(
                  "TEACHER_UNAVAILABLE",
                  teacher + " is marked unavailable in this period",
                  "ERROR"));
        }
      }
      if (hasText(room)) {
        String key = roomKey(room, day, periodId);
        if (!roomCells.add(key) || externalRoomBusy.contains(key)) {
          conflicts.add(
              conflict("ROOM_COLLISION", room + " is already occupied in this period", "ERROR"));
        }
        TimetableRoomEntity configured = roomByName.get(lower(room));
        if (configured != null
            && section.getCapacity() != null
            && configured.getCapacity() < section.getCapacity()) {
          conflicts.add(
              conflict(
                  "ROOM_CAPACITY",
                  room
                      + " holds "
                      + configured.getCapacity()
                      + " but this section requires "
                      + section.getCapacity(),
                  "ERROR"));
        }
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("valid", conflicts.stream().noneMatch(c -> "ERROR".equals(c.get("severity"))));
    result.put("conflicts", conflicts);
    result.put("checkedSlots", proposed.size());
    return result;
  }

  private ClassSectionEntity requireSection(UUID sectionId, TenantScope scope) {
    if (sectionId == null) {
      throw AcademicException.badRequest("sectionId is required");
    }
    return sections
        .findByIdAndOrganizationId(sectionId, scope.organizationId())
        .orElseThrow(() -> AcademicException.badRequest("Unknown sectionId"));
  }

  private List<TimetablePeriodEntity> scopedPeriods(TenantScope scope) {
    if (hasText(scope.branchId()) && hasText(scope.academicSessionId())) {
      return periods.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByPeriodNoAsc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId());
    }
    return periods.findByOrganizationIdOrderByPeriodNoAsc(scope.organizationId());
  }

  private String chooseRoom(
      TeachingAssignmentEntity assignment,
      ClassSectionEntity section,
      List<TimetableRoomEntity> usableRooms,
      Set<String> roomBusy,
      Cell cell) {
    List<String> preferred = new ArrayList<>();
    if (hasText(assignment.getPreferredRoom())) preferred.add(assignment.getPreferredRoom());
    if (hasText(section.getRoom())) preferred.add(section.getRoom());
    usableRooms.stream().map(TimetableRoomEntity::getName).forEach(preferred::add);
    return preferred.stream()
        .distinct()
        .filter(room -> !roomBusy.contains(roomKey(room, cell.day(), cell.periodId())))
        .findFirst()
        .orElse(null);
  }

  private boolean isUnavailable(TeachingAssignmentEntity assignment, Cell cell) {
    for (Map<String, Object> unavailable : assignment.getUnavailableSlots()) {
      if (intValue(unavailable.get("dayOfWeek"), -1) == cell.day()
          && cell.periodId().equals(uuid(unavailable.get("periodId")))) {
        return true;
      }
    }
    return false;
  }

  private Map<String, Object> generationResult(
      List<Map<String, Object>> generated,
      List<Map<String, Object>> conflicts,
      int requested,
      int placed) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("slots", generated);
    result.put("conflicts", conflicts);
    result.put("requestedPeriods", requested);
    result.put("placedPeriods", placed);
    result.put("complete", placed == requested && conflicts.stream().noneMatch(c -> "ERROR".equals(c.get("severity"))));
    return result;
  }

  private Map<String, Object> roomMap(TimetableRoomEntity room) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", room.getId().toString());
    out.put("name", room.getName());
    out.put("capacity", room.getCapacity());
    out.put("status", room.getStatus());
    return out;
  }

  private static Map<String, Object> conflict(String code, String message, String severity) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("code", code);
    out.put("message", message);
    out.put("severity", severity);
    return out;
  }

  private static List<Map<String, Object>> slotMaps(Object raw) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (!(raw instanceof List<?> list)) return out;
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        out.add(copy);
      }
    }
    return out;
  }

  private TenantScope requireStaff() {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    if (PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      throw new SecurityException("Timetable changes require a staff role: " + scope.roleCode());
    }
    return scope;
  }

  private static boolean inScope(String branch, String session, TenantScope scope) {
    return (!hasText(scope.branchId()) || !hasText(branch) || scope.branchId().equals(branch))
        && (!hasText(scope.academicSessionId())
            || !hasText(session)
            || scope.academicSessionId().equals(session));
  }

  private static String teacherKey(String teacher, int day, UUID periodId) {
    return lower(teacher) + "|" + day + "|" + periodId;
  }

  private static String roomKey(String room, int day, UUID periodId) {
    return lower(room) + "|" + day + "|" + periodId;
  }

  private static String lower(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String text(Object value) {
    if (value == null) return null;
    String out = String.valueOf(value).trim();
    return out.isEmpty() ? null : out;
  }

  private static int intValue(Object value, int fallback) {
    if (value instanceof Number n) return n.intValue();
    try {
      return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static UUID uuid(Object value) {
    try {
      return value == null ? null : UUID.fromString(String.valueOf(value));
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private record Cell(int day, UUID periodId, int periodNo) {
    String key() {
      return day + "|" + periodId;
    }
  }
}
