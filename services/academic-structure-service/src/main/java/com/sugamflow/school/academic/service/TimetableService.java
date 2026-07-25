package com.sugamflow.school.academic.service;

import com.sugamflow.school.academic.persistence.entity.TimetablePeriodEntity;
import com.sugamflow.school.academic.persistence.entity.TimetableSlotEntity;
import com.sugamflow.school.academic.persistence.repo.ClassSectionRepository;
import com.sugamflow.school.academic.persistence.repo.TimetablePeriodRepository;
import com.sugamflow.school.academic.persistence.repo.TimetableSlotRepository;
import com.sugamflow.school.academic.web.AcademicException;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.util.ArrayList;
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

  public TimetableService(
      TimetablePeriodRepository periods,
      TimetableSlotRepository slots,
      ClassSectionRepository sections,
      TimetableGenerationService generation) {
    this.periods = periods;
    this.slots = slots;
    this.sections = sections;
    this.generation = generation;
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
    return slots
        .findByOrganizationIdAndSectionIdOrderByDayOfWeekAsc(scope.organizationId(), sectionId)
        .stream()
        .map(AcademicMapper::slotToMap)
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
    return slots
        .findByOrganizationIdAndTeacherUsernameOrderByDayOfWeekAsc(scope.organizationId(), teacher)
        .stream()
        .map(AcademicMapper::slotToMap)
        .collect(Collectors.toList());
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

  // ---- helpers --------------------------------------------------------------

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
