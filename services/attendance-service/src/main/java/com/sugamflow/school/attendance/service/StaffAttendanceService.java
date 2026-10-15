package com.sugamflow.school.attendance.service;

import com.sugamflow.school.attendance.persistence.entity.StaffAttendanceMonthEntity;
import com.sugamflow.school.attendance.persistence.repo.StaffAttendanceMonthRepository;
import com.sugamflow.school.attendance.web.AttendanceException;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAttendanceService {

  private static final Set<String> STATUSES =
      Set.of("PRESENT", "ABSENT", "LATE", "LEAVE", "HALF_DAY");

  private final StaffAttendanceMonthRepository repository;

  public StaffAttendanceService(StaffAttendanceMonthRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> sheet(String yearMonth) {
    TenantScope scope = TenantContext.require();
    String month = requireMonth(yearMonth);
    return repository
        .findByOrganizationIdAndBranchIdAndYearMonth(
            scope.organizationId(), branchKey(scope), month)
        .map(this::toMap)
        .orElseGet(() -> empty(scope, month));
  }

  @Transactional
  public Map<String, Object> saveDay(String yearMonth, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String month = requireMonth(yearMonth);
    String date = body == null ? null : String.valueOf(body.get("date")).trim();
    if (date == null || date.isBlank() || "null".equals(date) || !date.startsWith(month)) {
      throw new AttendanceException("VALIDATION", "date must fall in " + month);
    }
    List<Map<String, Object>> incoming = marks(body.get("marks"), date);
    StaffAttendanceMonthEntity entity = loadOrCreate(scope, month);
    if ("SUBMITTED".equals(entity.getStatus())) {
      throw new AttendanceException("LOCKED", "This month is already submitted");
    }
    List<Map<String, Object>> kept = new ArrayList<>();
    for (Map<String, Object> existing : entity.getMarks()) {
      if (!date.equals(String.valueOf(existing.get("date")))) {
        kept.add(existing);
      }
    }
    kept.addAll(incoming);
    entity.setMarks(kept);
    entity.setUpdatedAt(Instant.now());
    return toMap(repository.save(entity));
  }

  /**
   * Updates one employee for one date. A submitted month is left unchanged.
   * Other employees on that date stay as they are.
   */
  @Transactional
  public void mergeBiometric(
      String organizationId,
      String branchId,
      LocalDate date,
      String staffId,
      String staffName,
      String status) {
    String month = YearMonth.from(date).toString();
    String branch = branchId == null || branchId.isBlank() ? "" : branchId.trim();
    String day = date.toString();
    StaffAttendanceMonthEntity entity =
        repository
            .findByOrganizationIdAndBranchIdAndYearMonth(organizationId, branch, month)
            .orElse(null);
    if (entity != null && "SUBMITTED".equals(entity.getStatus())) {
      return;
    }
    if (entity == null) {
      entity = new StaffAttendanceMonthEntity();
      entity.setId(UUID.randomUUID());
      entity.setOrganizationId(organizationId);
      entity.setBranchId(branch);
      entity.setYearMonth(month);
      entity.setStatus("DRAFT");
      entity.setMarks(new ArrayList<>());
    }
    String rosterStatus = "EARLY_DEPARTURE".equals(status) ? "PRESENT" : status;
    if (!STATUSES.contains(rosterStatus)) {
      rosterStatus = "PRESENT";
    }
    List<Map<String, Object>> kept = new ArrayList<>();
    for (Map<String, Object> existing : entity.getMarks()) {
      boolean sameDay = day.equals(String.valueOf(existing.get("date")));
      boolean sameStaff = staffId.equals(String.valueOf(existing.get("staffId")));
      if (!(sameDay && sameStaff)) {
        kept.add(existing);
      }
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("staffId", staffId);
    row.put("staffName", staffName == null ? "" : staffName);
    row.put("date", day);
    row.put("status", rosterStatus);
    row.put("source", "BIOMETRIC");
    kept.add(row);
    entity.setMarks(kept);
    entity.setUpdatedAt(Instant.now());
    repository.save(entity);
  }

  @Transactional
  public Map<String, Object> submit(String yearMonth) {
    TenantScope scope = TenantContext.require();
    String month = requireMonth(yearMonth);
    StaffAttendanceMonthEntity entity =
        repository
            .findByOrganizationIdAndBranchIdAndYearMonth(
                scope.organizationId(), branchKey(scope), month)
            .orElseThrow(
                () ->
                    new AttendanceException(
                        "VALIDATION", "Mark staff attendance before submitting " + month));
    if (entity.getMarks() == null || entity.getMarks().isEmpty()) {
      throw new AttendanceException(
          "VALIDATION", "Mark staff attendance before submitting " + month);
    }
    entity.setStatus("SUBMITTED");
    entity.setUpdatedAt(Instant.now());
    return toMap(repository.save(entity));
  }

  private StaffAttendanceMonthEntity loadOrCreate(TenantScope scope, String month) {
    return repository
        .findByOrganizationIdAndBranchIdAndYearMonth(
            scope.organizationId(), branchKey(scope), month)
        .orElseGet(
            () -> {
              StaffAttendanceMonthEntity created = new StaffAttendanceMonthEntity();
              created.setId(UUID.randomUUID());
              created.setOrganizationId(scope.organizationId());
              created.setBranchId(branchKey(scope));
              created.setYearMonth(month);
              created.setStatus("DRAFT");
              created.setMarks(new ArrayList<>());
              created.setUpdatedAt(Instant.now());
              return created;
            });
  }

  private Map<String, Object> empty(TenantScope scope, String month) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", scope.organizationId());
    out.put("branchId", branchKey(scope));
    out.put("yearMonth", month);
    out.put("status", "DRAFT");
    out.put("marks", List.of());
    out.put("submitted", false);
    return out;
  }

  private Map<String, Object> toMap(StaffAttendanceMonthEntity entity) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", entity.getId());
    out.put("organizationId", entity.getOrganizationId());
    out.put("branchId", entity.getBranchId());
    out.put("yearMonth", entity.getYearMonth());
    out.put("status", entity.getStatus());
    out.put("marks", entity.getMarks());
    out.put("markCount", entity.getMarks() == null ? 0 : entity.getMarks().size());
    out.put("submitted", "SUBMITTED".equals(entity.getStatus()));
    out.put("updatedAt", entity.getUpdatedAt());
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> marks(Object raw, String date) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      throw new AttendanceException("VALIDATION", "Add at least one staff mark");
    }
    List<Map<String, Object>> marks = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      String status = String.valueOf(map.get("status")).trim().toUpperCase(Locale.ROOT);
      if (!STATUSES.contains(status)) {
        throw new AttendanceException(
            "VALIDATION", "status must be one of PRESENT, ABSENT, LATE, LEAVE, HALF_DAY");
      }
      String staffId = String.valueOf(map.get("staffId")).trim();
      if (staffId.isBlank() || "null".equals(staffId)) {
        throw new AttendanceException("VALIDATION", "staffId is required");
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("staffId", staffId);
      Object name = map.get("staffName");
      row.put("staffName", name == null ? "" : String.valueOf(name));
      row.put("date", date);
      row.put("status", status);
      marks.add(row);
    }
    if (marks.isEmpty()) {
      throw new AttendanceException("VALIDATION", "Add at least one staff mark");
    }
    return marks;
  }

  private static String requireMonth(String yearMonth) {
    String raw = yearMonth == null ? "" : yearMonth.trim();
    try {
      return YearMonth.parse(raw).toString();
    } catch (Exception ex) {
      throw new AttendanceException("VALIDATION", "month must be yyyy-MM");
    }
  }

  private static String branchKey(TenantScope scope) {
    String branch = scope.branchId();
    return branch == null || branch.isBlank() ? "" : branch.trim();
  }
}
