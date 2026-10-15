package com.sugamflow.school.academic.service;

import com.sugamflow.school.academic.persistence.entity.TimetablePeriodEntity;
import com.sugamflow.school.academic.persistence.entity.TimetableSlotEntity;
import com.sugamflow.school.academic.persistence.entity.TimetableSubstituteEntity;
import com.sugamflow.school.academic.persistence.repo.ClassSectionRepository;
import com.sugamflow.school.academic.persistence.repo.TimetablePeriodRepository;
import com.sugamflow.school.academic.persistence.repo.TimetableSlotRepository;
import com.sugamflow.school.academic.persistence.repo.TimetableSubstituteRepository;
import com.sugamflow.school.academic.web.AcademicException;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Period definitions and per-section weekly timetable slots. */
@Service
public class TimetableService {

  private final TimetablePeriodRepository periods;
  private final TimetableSlotRepository slots;
  private final ClassSectionRepository sections;
  private final TimetableGenerationService generation;
  private final TimetableSubstituteRepository substitutes;

  public TimetableService(
      TimetablePeriodRepository periods,
      TimetableSlotRepository slots,
      ClassSectionRepository sections,
      TimetableGenerationService generation,
      TimetableSubstituteRepository substitutes) {
    this.periods = periods;
    this.slots = slots;
    this.sections = sections;
    this.generation = generation;
    this.substitutes = substitutes;
  }

  // ---- periods --------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listPeriods() {
    TenantScope scope = TenantContext.require();
    List<TimetablePeriodEntity> found =
        hasSessionScope(scope)
            ? periods.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByPeriodNoAsc(
                scope.organizationId(), scope.branchId(), scope.academicSessionId())
            : periods.findByOrganizationIdOrderByPeriodNoAsc(scope.organizationId());
    return found.stream().map(AcademicMapper::periodToMap).collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> createPeriod(Map<String, Object> body) {
    TenantScope scope = requireStaff();
    String label = RequestValues.str(body, "label");
    if (label == null) {
      throw AcademicException.badRequest("Period label is required");
    }
    int periodNo = RequestValues.intOr(body, "periodNo", 0);
    assertPeriodNoAvailable(scope, periodNo, null);
    TimetablePeriodEntity e = new TimetablePeriodEntity();
    e.setId(UUID.randomUUID());
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(scope.branchId());
    e.setAcademicSessionId(scope.academicSessionId());
    e.setPeriodNo(periodNo);
    e.setLabel(label);
    e.setStartTime(RequestValues.str(body, "startTime"));
    e.setEndTime(RequestValues.str(body, "endTime"));
    e.setBreakPeriod(RequestValues.bool(body, "breakPeriod"));
    return AcademicMapper.periodToMap(periods.save(e));
  }

  @Transactional
  public Map<String, Object> updatePeriod(UUID id, Map<String, Object> body) {
    TenantScope scope = requireStaff();
    TimetablePeriodEntity e =
        periods
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Period"));
    if (body.containsKey("periodNo")) {
      int periodNo = RequestValues.intOr(body, "periodNo", e.getPeriodNo());
      assertPeriodNoAvailable(scope, periodNo, id);
      e.setPeriodNo(periodNo);
    }
    if (RequestValues.str(body, "label") != null) {
      e.setLabel(RequestValues.str(body, "label"));
    }
    if (body.containsKey("startTime")) {
      e.setStartTime(RequestValues.str(body, "startTime"));
    }
    if (body.containsKey("endTime")) {
      e.setEndTime(RequestValues.str(body, "endTime"));
    }
    if (body.containsKey("breakPeriod")) {
      e.setBreakPeriod(RequestValues.bool(body, "breakPeriod"));
    }
    e.setUpdatedAt(Instant.now());
    return AcademicMapper.periodToMap(periods.save(e));
  }

  @Transactional
  public void deletePeriod(UUID id) {
    TenantScope scope = requireStaff();
    TimetablePeriodEntity e =
        periods
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Period"));
    periods.delete(e);
  }

  // ---- slots ----------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listSlotsForSection(UUID sectionId) {
    TenantScope scope = TenantContext.require();
    if (sectionId == null) {
      throw AcademicException.badRequest("sectionId is required");
    }
    List<TimetableSlotEntity> rows =
        slots.findByOrganizationIdAndSectionIdOrderByDayOfWeekAsc(scope.organizationId(), sectionId);
    Map<UUID, TimetableSubstituteEntity> nextCover = nextCovers(rows);
    return rows.stream()
        .map(slot -> withCover(AcademicMapper.slotToMap(slot), nextCover.get(slot.getId())))
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listSlotsForTeacher(String teacherUsername) {
    TenantScope scope = TenantContext.require();
    String teacher = teacherUsername;
    if (teacher == null || teacher.isBlank()) {
      teacher = scope.userId();
    }
    if (teacher == null || teacher.isBlank()) {
      return List.of();
    }
    List<Map<String, Object>> mine =
        slots
            .findByOrganizationIdAndTeacherUsernameOrderByDayOfWeekAsc(
                scope.organizationId(), teacher)
            .stream()
            .map(AcademicMapper::slotToMap)
            .collect(Collectors.toList());
    LocalDate today = LocalDate.now();
    for (TimetableSubstituteEntity cover :
        substitutes.findByOrganizationIdAndTeacherUsernameAndSubstituteDate(
            scope.organizationId(), teacher, today)) {
      slots
          .findByIdAndOrganizationId(cover.getSlotId(), scope.organizationId())
          .ifPresent(
              slot -> {
                Map<String, Object> mapped = withCover(AcademicMapper.slotToMap(slot), cover);
                mapped.put("substitute", true);
                mine.add(mapped);
              });
    }
    return mine;
  }

  /** Covers one period on one date. The weekly teacher on the slot is left as-is. */
  @Transactional
  public Map<String, Object> assignSubstitute(UUID slotId, Map<String, Object> body) {
    TenantScope scope = requireStaff();
    TimetableSlotEntity slot =
        slots
            .findByIdAndOrganizationId(slotId, scope.organizationId())
            .orElseThrow(() -> AcademicException.badRequest("Unknown timetable slot"));
    String teacher = body == null ? null : string(body.get("teacherUsername"));
    LocalDate date = date(body == null ? null : body.get("substituteDate"));
    if (teacher == null || teacher.isBlank() || date == null) {
      throw AcademicException.badRequest("teacherUsername and substituteDate are required");
    }
    if (date.getDayOfWeek().getValue() != slot.getDayOfWeek()) {
      throw AcademicException.badRequest(
          "Substitute date must fall on the same weekday as this period");
    }
    if (teacher.equalsIgnoreCase(slot.getTeacherUsername())) {
      throw AcademicException.badRequest("Pick a teacher other than the weekly teacher");
    }
    assertSubstituteFree(scope, slot, teacher, date);
    Instant now = Instant.now();
    TimetableSubstituteEntity row =
        substitutes
            .findBySlotIdAndSubstituteDate(slot.getId(), date)
            .orElseGet(
                () -> {
                  TimetableSubstituteEntity created = new TimetableSubstituteEntity();
                  created.setId(UUID.randomUUID());
                  created.setOrganizationId(scope.organizationId());
                  created.setSlotId(slot.getId());
                  created.setCreatedAt(now);
                  return created;
                });
    row.setSubstituteDate(date);
    row.setTeacherUsername(teacher.trim());
    row.setNote(string(body.get("note")));
    row.setUpdatedAt(now);
    return withCover(AcademicMapper.slotToMap(slot), substitutes.save(row));
  }

  /** Replaces the entire weekly grid for a section in one call. */
  @Transactional
  public List<Map<String, Object>> replaceSectionTimetable(UUID sectionId, Map<String, Object> body) {
    TenantScope scope = requireStaff();
    if (sectionId == null) {
      throw AcademicException.badRequest("sectionId is required");
    }
    sections
        .findByIdAndOrganizationId(sectionId, scope.organizationId())
        .orElseThrow(() -> AcademicException.badRequest("Unknown sectionId"));

    Object raw = body == null ? null : body.get("slots");
    if (!(raw instanceof List<?> list)) {
      throw AcademicException.badRequest("slots array is required");
    }

    Map<String, Object> validation = generation.validate(sectionId, body);
    if (!Boolean.TRUE.equals(validation.get("valid")) && !RequestValues.bool(body, "allowConflicts")) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> conflicts =
          (List<Map<String, Object>>) validation.getOrDefault("conflicts", List.of());
      String message =
          conflicts.isEmpty()
              ? "Timetable contains conflicts"
              : String.valueOf(conflicts.get(0).get("message"));
      throw AcademicException.badRequest(
          message + ". Resolve conflicts or explicitly save with allowConflicts.");
    }

    // Flush the delete before inserts so uq_timetable_slot_section_cell is not violated
    // when replacing an existing Monday/Period cell in the same transaction.
    slots.deleteByOrganizationIdAndSectionId(scope.organizationId(), sectionId);
    slots.flush();

    List<TimetableSlotEntity> toSave = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> m)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> slot = (Map<String, Object>) m;
      UUID periodId = RequestValues.uuid(slot, "periodId");
      if (periodId == null) {
        throw AcademicException.badRequest("Each slot requires a periodId");
      }
      TimetableSlotEntity e = new TimetableSlotEntity();
      e.setId(UUID.randomUUID());
      e.setOrganizationId(scope.organizationId());
      e.setBranchId(scope.branchId());
      e.setAcademicSessionId(scope.academicSessionId());
      e.setSectionId(sectionId);
      e.setDayOfWeek(RequestValues.intOr(slot, "dayOfWeek", 1));
      e.setPeriodId(periodId);
      e.setSubjectId(RequestValues.uuid(slot, "subjectId"));
      e.setTeacherUsername(RequestValues.str(slot, "teacherUsername"));
      e.setRoom(RequestValues.str(slot, "room"));
      toSave.add(e);
    }
    return slots.saveAll(toSave).stream()
        .map(AcademicMapper::slotToMap)
        .collect(Collectors.toList());
  }

  private Map<UUID, TimetableSubstituteEntity> nextCovers(List<TimetableSlotEntity> rows) {
    Map<UUID, TimetableSubstituteEntity> next = new LinkedHashMap<>();
    if (rows.isEmpty()) {
      return next;
    }
    List<UUID> ids = rows.stream().map(TimetableSlotEntity::getId).toList();
    for (TimetableSubstituteEntity cover :
        substitutes.findBySlotIdInAndSubstituteDateGreaterThanEqual(ids, LocalDate.now())) {
      TimetableSubstituteEntity existing = next.get(cover.getSlotId());
      if (existing == null || cover.getSubstituteDate().isBefore(existing.getSubstituteDate())) {
        next.put(cover.getSlotId(), cover);
      }
    }
    return next;
  }

  private void assertSubstituteFree(
      TenantScope scope, TimetableSlotEntity slot, String teacher, LocalDate date) {
    for (TimetableSlotEntity other :
        slots.findByOrganizationIdAndDayOfWeekAndPeriodId(
            scope.organizationId(), slot.getDayOfWeek(), slot.getPeriodId())) {
      if (other.getId().equals(slot.getId())) {
        continue;
      }
      if (teacher.equalsIgnoreCase(other.getTeacherUsername())) {
        throw AcademicException.badRequest(
            "That teacher already has this period on the weekly timetable");
      }
      substitutes
          .findBySlotIdAndSubstituteDate(other.getId(), date)
          .filter(cover -> teacher.equalsIgnoreCase(cover.getTeacherUsername()))
          .ifPresent(
              cover -> {
                throw AcademicException.badRequest(
                    "That teacher is already covering another class in this period");
              });
    }
  }

  private static Map<String, Object> withCover(
      Map<String, Object> slot, TimetableSubstituteEntity cover) {
    if (cover == null) {
      return slot;
    }
    slot.put("substituteDate", cover.getSubstituteDate().toString());
    slot.put("substituteTeacher", cover.getTeacherUsername());
    slot.put("substituteNote", cover.getNote());
    return slot;
  }

  private static String string(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
  }

  private static LocalDate date(Object value) {
    String text = string(value);
    if (text == null) {
      return null;
    }
    try {
      return LocalDate.parse(text);
    } catch (DateTimeParseException ex) {
      throw AcademicException.badRequest("substituteDate must be yyyy-MM-dd");
    }
  }

  private void assertPeriodNoAvailable(TenantScope scope, int periodNo, UUID exceptId) {
    Optional<TimetablePeriodEntity> existing =
        hasSessionScope(scope)
            ? periods.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndPeriodNo(
                scope.organizationId(), scope.branchId(), scope.academicSessionId(), periodNo)
            : periods.findByOrganizationIdAndPeriodNo(scope.organizationId(), periodNo);
    if (existing.isPresent() && (exceptId == null || !existing.get().getId().equals(exceptId))) {
      throw AcademicException.badRequest(
          "Period number " + periodNo + " already exists for this branch/session");
    }
  }

  private TenantScope requireStaff() {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    if (PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      throw new SecurityException("Timetable changes require a staff role: " + scope.roleCode());
    }
    return scope;
  }

  private static boolean hasSessionScope(TenantScope scope) {
    return scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank();
  }
}
