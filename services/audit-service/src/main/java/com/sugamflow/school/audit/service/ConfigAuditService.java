package com.sugamflow.school.audit.service;

import com.sugamflow.school.audit.integration.ConfigEngineClient;
import com.sugamflow.school.audit.persistence.entity.ConfigChangeAuditEntity;
import com.sugamflow.school.audit.persistence.repo.ConfigChangeAuditRepository;
import com.sugamflow.school.audit.web.AuditException;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigAuditService {

  public static final String FEATURE_AUDIT_LOGS = "FEATURE_AUDIT_LOGS";

  public static final List<String> ENTITY_TYPES =
      List.of("DESIGN_THEME", "MODULE_SETTINGS", "LOCALIZATION", "MENU_CONFIG");

  public static final List<String> STATUSES =
      List.of("PENDING_APPROVAL", "APPROVED", "ROLLED_BACK");

  private final ConfigChangeAuditRepository repo;
  private final ConfigEngineClient engines;

  public ConfigAuditService(ConfigChangeAuditRepository repo, ConfigEngineClient engines) {
    this.repo = repo;
    this.engines = engines;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    boolean enabled = engines.isFeatureEnabled(scope, FEATURE_AUDIT_LOGS);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", enabled);
    out.put("requiredFeatureFlag", FEATURE_AUDIT_LOGS);
    out.put("entityTypes", ENTITY_TYPES);
    out.put("statuses", STATUSES);
    out.put(
        "capabilities",
        Map.of(
            "approve", true,
            "rollback", true,
            "applyRollbackToSettings", true,
            "filter", true));
    return out;
  }

  @Transactional
  public Map<String, Object> record(
      String org, String branch, String userId, Map<String, Object> body) {
    requireFeature();
    ConfigChangeAuditEntity e = new ConfigChangeAuditEntity();
    e.setId(UUID.randomUUID().toString());
    e.setOrganizationId(org);
    e.setBranchId(branch);
    e.setEntityType(asString(body.get("entityType")));
    e.setEntityKey(asString(body.get("entityKey")));
    e.setStatus(String.valueOf(body.getOrDefault("status", "PENDING_APPROVAL")));
    e.setChangedBy(userId == null ? "system" : userId);
    e.setReason(asString(body.get("reason")));
    e.setOldValue(asMap(body.get("oldValue")));
    e.setNewValue(asMap(body.get("newValue")));
    e.setCreatedAt(Instant.now());
    return toMap(repo.save(e));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(
      String org, String entityType, String status, String entityKey) {
    requireFeature();
    return repo.search(org, entityType, status, entityKey).stream()
        .map(this::toMap)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(String id) {
    requireFeature();
    ConfigChangeAuditEntity e = requireOwned(id);
    return toMap(e);
  }

  @Transactional
  public Map<String, Object> approve(String id) {
    requireFeature();
    ConfigChangeAuditEntity e = requireOwned(id);
    if (!"PENDING_APPROVAL".equals(e.getStatus())) {
      throw new AuditException(
          "INVALID_STATUS", "Only PENDING_APPROVAL entries can be approved (status=" + e.getStatus() + ")");
    }
    e.setStatus("APPROVED");
    e.setApprovedAt(Instant.now());
    return toMap(repo.save(e));
  }

  @Transactional
  public Map<String, Object> rollback(String id, String userId, String reason) {
    requireFeature();
    TenantScope scope = TenantContext.require();
    ConfigChangeAuditEntity original = requireOwned(id);
    if ("ROLLED_BACK".equals(original.getStatus())) {
      throw new AuditException("ALREADY_ROLLED_BACK", "This change was already rolled back");
    }
    if (!"APPROVED".equals(original.getStatus()) && !"PENDING_APPROVAL".equals(original.getStatus())) {
      throw new AuditException(
          "INVALID_STATUS",
          "Rollback requires APPROVED or PENDING_APPROVAL (status=" + original.getStatus() + ")");
    }
    if (original.getOldValue() == null) {
      throw new AuditException("NO_OLD_VALUE", "Cannot rollback — no previous value stored");
    }

    applyRestore(scope, original);

    original.setStatus("ROLLED_BACK");
    repo.save(original);

    ConfigChangeAuditEntity rb = new ConfigChangeAuditEntity();
    rb.setId(UUID.randomUUID().toString());
    rb.setOrganizationId(original.getOrganizationId());
    rb.setBranchId(original.getBranchId());
    rb.setEntityType(original.getEntityType());
    rb.setEntityKey(original.getEntityKey());
    rb.setOldValue(original.getNewValue());
    rb.setNewValue(original.getOldValue());
    rb.setReason(
        reason != null && !reason.isBlank()
            ? reason
            : "Rollback of " + id);
    rb.setChangedBy(userId == null ? "system" : userId);
    rb.setStatus("APPROVED");
    rb.setRollbackOf(id);
    rb.setApprovedAt(Instant.now());
    rb.setCreatedAt(Instant.now());
    return toMap(repo.save(rb));
  }

  private void applyRestore(TenantScope scope, ConfigChangeAuditEntity original) {
    String type = original.getEntityType() == null ? "" : original.getEntityType();
    Map<String, Object> restore = original.getOldValue();
    switch (type) {
      case "DESIGN_THEME" -> engines.restoreDesignTheme(scope, restore);
      case "MODULE_SETTINGS" ->
          engines.restoreModuleSettings(scope, original.getEntityKey(), restore);
      case "LOCALIZATION" -> engines.restoreLocalization(scope, restore);
      case "MENU_CONFIG" -> engines.restoreMenus(scope, restore);
      default -> {
        // Audit-only rollback for unknown entity types (sample / future writers).
      }
    }
  }

  private void requireFeature() {
    TenantScope scope = TenantContext.require();
    if (!engines.isFeatureEnabled(scope, FEATURE_AUDIT_LOGS)) {
      throw new AuditException(
          "FEATURE_DISABLED", "FEATURE_AUDIT_LOGS is off for this subscription plan.");
    }
  }

  private ConfigChangeAuditEntity requireOwned(String id) {
    TenantScope scope = TenantContext.require();
    ConfigChangeAuditEntity e = repo.findById(id).orElse(null);
    if (e == null) {
      throw new AuditException("NOT_FOUND", "Config change not found: " + id);
    }
    if (!scope.organizationId().equals(e.getOrganizationId())) {
      throw new AuditException("NOT_FOUND", "Config change not found: " + id);
    }
    return e;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    if (v instanceof List<?> list) {
      Map<String, Object> wrap = new LinkedHashMap<>();
      wrap.put("nodes", list);
      return wrap;
    }
    Map<String, Object> wrap = new LinkedHashMap<>();
    wrap.put("value", v);
    return wrap;
  }

  private String asString(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private Map<String, Object> toMap(ConfigChangeAuditEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("organizationId", e.getOrganizationId());
    m.put("branchId", e.getBranchId());
    m.put("entityType", e.getEntityType());
    m.put("entityKey", e.getEntityKey());
    m.put("status", e.getStatus());
    m.put("changedBy", e.getChangedBy());
    m.put("reason", e.getReason());
    m.put("oldValue", e.getOldValue());
    m.put("newValue", e.getNewValue());
    m.put("rollbackOf", e.getRollbackOf());
    m.put("approvedAt", e.getApprovedAt() == null ? null : e.getApprovedAt().toString());
    m.put("timestamp", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
    m.put("canApprove", "PENDING_APPROVAL".equals(e.getStatus()));
    m.put(
        "canRollback",
        ("APPROVED".equals(e.getStatus()) || "PENDING_APPROVAL".equals(e.getStatus()))
            && e.getOldValue() != null
            && e.getRollbackOf() == null);
    return m;
  }
}
