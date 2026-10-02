package com.sugamflow.school.student.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.persistence.entity.CampusDeskItemEntity;
import com.sugamflow.school.student.persistence.repo.CampusDeskItemRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusDeskService {

  private static final Set<String> KINDS =
      Set.of(
          "LEAVE",
          "LEAVE_TYPE",
          "GATE_PASS",
          "VISIT_PURPOSE",
          "EXIT_GATE",
          "INVENTORY",
          "PTM",
          "TASK",
          "TEACHER_DIARY",
          "DESK_SLIP",
          "ACTIVITY",
          "LECTURE");

  private final CampusDeskItemRepository repository;

  public CampusDeskService(CampusDeskItemRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String kind) {
    TenantScope scope = TenantContext.require();
    String normalized = normalizeKind(kind);
    return repository
        .findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), normalized)
        .stream()
        .filter(item -> sameCampus(scope, item))
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> create(String kind, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String normalized = normalizeKind(kind);
    CampusDeskItemEntity entity = new CampusDeskItemEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(blankToNull(scope.branchId()));
    entity.setAcademicSessionId(blankToNull(scope.academicSessionId()));
    entity.setKind(normalized);
    entity.setStatus(text(body.get("status"), defaultStatus(normalized)));
    entity.setSubjectType(text(body.get("subjectType"), "STUDENT"));
    entity.setSubjectRef(text(body.get("subjectRef"), null));
    entity.setSubjectName(text(body.get("subjectName"), null));
    entity.setTitle(text(body.get("title"), normalized.replace('_', ' ')));
    entity.setNote(text(body.get("note"), null));
    entity.setPayload(payloadOf(body.get("payload")));
    entity.setCreatedBy(scope.userId());
    Instant now = Instant.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return toDto(repository.save(entity));
  }

  @Transactional
  public Map<String, Object> updateStatus(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    CampusDeskItemEntity entity =
        repository
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> new StudentException("NOT_FOUND", "Desk item not found"));
    String status = text(body.get("status"), null);
    if (status == null) {
      throw new StudentException("VALIDATION", "status is required");
    }
    entity.setStatus(status.toUpperCase(Locale.ROOT));
    String note = text(body.get("note"), null);
    if (note != null) {
      entity.setNote(note);
    }
    if ("LEAVE".equals(entity.getKind()) && "APPROVED".equals(entity.getStatus())) {
      applyLeaveBalance(scope, entity);
    }
    entity.setUpdatedAt(Instant.now());
    return toDto(repository.save(entity));
  }

  private static boolean sameCampus(TenantScope scope, CampusDeskItemEntity item) {
    if (scope.branchId() == null || scope.branchId().isBlank()) {
      return true;
    }
    return scope.branchId().equals(item.getBranchId()) || item.getBranchId() == null;
  }

  private static String normalizeKind(String kind) {
    String normalized = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
    if (!KINDS.contains(normalized)) {
      throw new StudentException("VALIDATION", "Unsupported desk kind");
    }
    return normalized;
  }

  private static String defaultStatus(String kind) {
    return switch (kind) {
      case "LEAVE", "PTM", "TASK" -> "PENDING";
      case "GATE_PASS", "DESK_SLIP" -> "OPEN";
      case "LECTURE" -> "PRESENT";
      default -> "ACTIVE";
    };
  }

  private static Map<String, Object> payloadOf(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      map.forEach((key, value) -> out.put(String.valueOf(key), value));
      return out;
    }
    return new LinkedHashMap<>();
  }

  private static String text(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? fallback : text;
  }

  private void applyLeaveBalance(TenantScope scope, CampusDeskItemEntity entity) {
    int requested = requestedDays(entity);
    int remaining = remainingDays(scope, entity, true);
    if (requested > remaining) {
      throw new StudentException(
          "LEAVE_BALANCE",
          "Only " + remaining + " day(s) remain for this leave type");
    }
    Map<String, Object> payload =
        entity.getPayload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getPayload());
    payload.put("days", requested);
    payload.put("remainingDays", remaining - requested);
    entity.setPayload(payload);
  }

  private int remainingDays(TenantScope scope, CampusDeskItemEntity entity, boolean excludeSelf) {
    return Math.max(0, quotaFor(scope, entity) - usedDays(scope, entity, excludeSelf));
  }

  private int quotaFor(TenantScope scope, CampusDeskItemEntity leave) {
    String type = leaveType(leave);
    return repository.findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), "LEAVE_TYPE")
        .stream()
        .filter(item -> sameCampus(scope, item))
        .filter(item -> type.equalsIgnoreCase(item.getTitle()))
        .map(CampusDeskService::yearlyQuota)
        .findFirst()
        .orElse(12);
  }

  private int usedDays(TenantScope scope, CampusDeskItemEntity leave, boolean excludeSelf) {
    String type = leaveType(leave);
    String person = personKey(leave);
    int year = leave.getCreatedAt() == null
        ? Instant.now().atZone(ZoneId.of("Asia/Kolkata")).getYear()
        : leave.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).getYear();
    int used = 0;
    for (CampusDeskItemEntity item :
        repository.findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), "LEAVE")) {
      if (!sameCampus(scope, item) || !"APPROVED".equalsIgnoreCase(item.getStatus())) {
        continue;
      }
      if (excludeSelf && item.getId().equals(leave.getId())) {
        continue;
      }
      if (!type.equalsIgnoreCase(leaveType(item)) || !person.equalsIgnoreCase(personKey(item))) {
        continue;
      }
      int itemYear = item.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).getYear();
      if (itemYear == year) {
        used += requestedDays(item);
      }
    }
    return used;
  }

  private static int yearlyQuota(CampusDeskItemEntity type) {
    Object raw = type.getPayload() == null ? null : type.getPayload().get("yearlyQuota");
    if (raw instanceof Number number) {
      return Math.max(0, number.intValue());
    }
    if (raw != null) {
      try {
        return Math.max(0, Integer.parseInt(String.valueOf(raw).trim()));
      } catch (NumberFormatException ignored) {
        return 12;
      }
    }
    return 12;
  }

  private static int requestedDays(CampusDeskItemEntity leave) {
    Object raw = leave.getPayload() == null ? null : leave.getPayload().get("days");
    if (raw instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    if (raw != null) {
      try {
        int parsed = Integer.parseInt(String.valueOf(raw).trim());
        if (parsed > 0) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        return 1;
      }
    }
    return 1;
  }

  private static String leaveType(CampusDeskItemEntity leave) {
    Object key = leave.getPayload() == null ? null : leave.getPayload().get("catalogKey");
    String fromPayload = text(key, null);
    return fromPayload != null ? fromPayload : text(leave.getTitle(), "LEAVE");
  }

  private static String personKey(CampusDeskItemEntity leave) {
    String ref = text(leave.getSubjectRef(), null);
    return ref != null ? ref : text(leave.getSubjectName(), "");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private Map<String, Object> toDto(CampusDeskItemEntity entity) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", entity.getId().toString());
    row.put("kind", entity.getKind());
    row.put("status", entity.getStatus());
    row.put("subjectType", entity.getSubjectType());
    row.put("subjectRef", entity.getSubjectRef());
    row.put("subjectName", entity.getSubjectName());
    row.put("title", entity.getTitle());
    row.put("note", entity.getNote());
    row.put("payload", entity.getPayload());
    if ("LEAVE".equals(entity.getKind())) {
      row.put("requestedDays", requestedDays(entity));
      row.put("remainingDays", remainingDays(TenantContext.require(), entity, false));
    }
    if ("LEAVE_TYPE".equals(entity.getKind())) {
      row.put("yearlyQuota", yearlyQuota(entity));
    }
    row.put("createdBy", entity.getCreatedBy());
    row.put("createdAt", entity.getCreatedAt().toString());
    row.put("updatedAt", entity.getUpdatedAt().toString());
    return row;
  }
}
