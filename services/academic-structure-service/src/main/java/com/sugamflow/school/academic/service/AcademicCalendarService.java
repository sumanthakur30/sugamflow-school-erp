package com.sugamflow.school.academic.service;

import com.sugamflow.school.academic.persistence.entity.AcademicCalendarEntity;
import com.sugamflow.school.academic.persistence.repo.AcademicCalendarRepository;
import com.sugamflow.school.academic.web.AcademicException;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.time.LocalDate;
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
public class AcademicCalendarService {

  private static final List<String> DEFAULT_WORKING =
      List.of("MON", "TUE", "WED", "THU", "FRI");
  private static final Set<String> WEEK =
      Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

  private final AcademicCalendarRepository repository;

  public AcademicCalendarService(AcademicCalendarRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get() {
    TenantScope scope = TenantContext.require();
    return repository
        .findByOrganizationIdAndBranchIdAndAcademicSessionId(
            scope.organizationId(), branchKey(scope), sessionKey(scope))
        .map(this::toMap)
        .orElseGet(() -> defaults(scope));
  }

  @Transactional
  public Map<String, Object> save(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    List<String> days = workingDays(body == null ? null : body.get("workingDays"));
    if (days.isEmpty()) {
      throw AcademicException.badRequest("Choose at least one working day");
    }
    List<Map<String, Object>> holidays = holidays(body == null ? null : body.get("holidays"));
    String branch = branchKey(scope);
    String session = sessionKey(scope);
    AcademicCalendarEntity entity =
        repository
            .findByOrganizationIdAndBranchIdAndAcademicSessionId(
                scope.organizationId(), branch, session)
            .orElseGet(AcademicCalendarEntity::new);
    if (entity.getId() == null) {
      entity.setId(UUID.randomUUID());
      entity.setOrganizationId(scope.organizationId());
      entity.setBranchId(branch);
      entity.setAcademicSessionId(session);
    }
    entity.setWorkingDays(days);
    entity.setHolidays(holidays);
    entity.setUpdatedAt(Instant.now());
    return toMap(repository.save(entity));
  }

  private Map<String, Object> defaults(TenantScope scope) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", scope.organizationId());
    out.put("branchId", branchKey(scope));
    out.put("academicSessionId", sessionKey(scope));
    out.put("workingDays", DEFAULT_WORKING);
    out.put("holidays", List.of());
    out.put("saved", false);
    return out;
  }

  private Map<String, Object> toMap(AcademicCalendarEntity entity) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", entity.getId());
    out.put("organizationId", entity.getOrganizationId());
    out.put("branchId", entity.getBranchId());
    out.put("academicSessionId", entity.getAcademicSessionId());
    out.put("workingDays", entity.getWorkingDays());
    out.put("holidays", entity.getHolidays());
    out.put("updatedAt", entity.getUpdatedAt());
    out.put("saved", true);
    return out;
  }

  private static List<String> workingDays(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> days = new ArrayList<>();
    for (Object item : list) {
      String day = String.valueOf(item).trim().toUpperCase(Locale.ROOT);
      if (WEEK.contains(day) && !days.contains(day)) {
        days.add(day);
      }
    }
    return days;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> holidays(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> holidays = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      String date = String.valueOf(map.get("date")).trim();
      String name = String.valueOf(map.get("name")).trim();
      if (date.isBlank() || "null".equals(date) || name.isBlank() || "null".equals(name)) {
        continue;
      }
      try {
        LocalDate.parse(date);
      } catch (Exception ex) {
        throw AcademicException.badRequest("Holiday date must be yyyy-MM-dd");
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("date", date);
      row.put("name", name);
      holidays.add(row);
    }
    return holidays;
  }

  private static String branchKey(TenantScope scope) {
    String branch = scope.branchId();
    return branch == null || branch.isBlank() ? "" : branch.trim();
  }

  private static String sessionKey(TenantScope scope) {
    String session = scope.academicSessionId();
    return session == null || session.isBlank() ? "" : session.trim();
  }
}
