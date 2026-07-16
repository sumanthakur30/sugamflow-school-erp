package com.sugamflow.school.library.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.library.integration.ConfigEngineClient;
import com.sugamflow.school.library.ops.LibraryOpsCatalog;
import com.sugamflow.school.library.persistence.entity.OpsDefinitionEntity;
import com.sugamflow.school.library.persistence.repo.OpsDefinitionRepository;
import com.sugamflow.school.library.web.LibraryException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryOpsService {

  private final OpsDefinitionRepository definitionRepo;
  private final ConfigEngineClient engines;

  public LibraryOpsService(OpsDefinitionRepository definitionRepo, ConfigEngineClient engines) {
    this.definitionRepo = definitionRepo;
    this.engines = engines;
  }

  @Transactional
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    ensureDefaults(scope);
    Map<String, Object> module = engines.getModuleSettings(scope, LibraryRecordService.MODULE_LIBRARY);
    Map<String, Object> settings = moduleSettingsMap(module);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("settings", settings);
    out.put("categories", listDefinitions(LibraryOpsCatalog.TYPE_BOOK_CATEGORY));
    out.put("finePolicies", listDefinitions(LibraryOpsCatalog.TYPE_FINE_POLICY));
    out.put("circulationPolicies", listDefinitions(LibraryOpsCatalog.TYPE_CIRCULATION_POLICY));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listDefinitions(String type) {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    return definitionRepo
        .findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
            scope.organizationId(), type, "ACTIVE")
        .stream()
        .map(this::toDefinitionDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> saveDefinition(String type, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    String key = stringOr(body.get("definitionKey"), stringOr(body.get("code"), null));
    if (key == null || key.isBlank()) {
      throw new LibraryException("VALIDATION", "definitionKey is required");
    }
    key = key.trim().toUpperCase().replace(' ', '_');
    Map<String, Object> payload = new LinkedHashMap<>(body);
    payload.put("definitionKey", key);
    payload.remove("id");
    payload.remove("version");

    OpsDefinitionEntity existing =
        definitionRepo
            .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                scope.organizationId(), type, key, "ACTIVE")
            .orElse(null);

    OpsDefinitionEntity entity = new OpsDefinitionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setDefinitionType(type);
    entity.setDefinitionKey(key);
    entity.setStatus("ACTIVE");
    entity.setVersion(existing == null ? 1 : existing.getVersion() + 1);
    entity.setPayload(payload);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    if (existing != null) {
      existing.setStatus("SUPERSEDED");
      existing.setUpdatedAt(Instant.now());
      definitionRepo.save(existing);
    }
    return toDefinitionDto(definitionRepo.save(entity));
  }

  @Transactional
  public Map<String, Object> previewFine(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    ensureDefaults(scope);
    Map<String, Object> settings = moduleSettingsMap(engines.getModuleSettings(scope, LibraryRecordService.MODULE_LIBRARY));
    String policyKey =
        stringOr(body.get("finePolicyKey"), stringOr(settings.get("defaultFinePolicyKey"), "default_fine"));
    Map<String, Object> policy = requireDefinition(scope, LibraryOpsCatalog.TYPE_FINE_POLICY, policyKey);
    int overdueDays = toInt(body.get("overdueDays"), 0);
    int graceDays = toInt(policy.get("graceDays"), 0);
    BigDecimal perDay = toDecimal(policy.get("perDayRate"));
    BigDecimal maxFine = toDecimal(policy.get("maxFine"));
    int chargeableDays = Math.max(0, overdueDays - graceDays);
    BigDecimal fine = perDay.multiply(BigDecimal.valueOf(chargeableDays));
    if (maxFine.compareTo(BigDecimal.ZERO) > 0 && fine.compareTo(maxFine) > 0) {
      fine = maxFine;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("finePolicyKey", policyKey);
    out.put("overdueDays", overdueDays);
    out.put("graceDays", graceDays);
    out.put("chargeableDays", chargeableDays);
    out.put("fineAmount", fine.setScale(2, RoundingMode.HALF_UP));
    out.put("currency", stringOr(policy.get("currency"), "INR"));
    return out;
  }

  @Transactional
  public void ensureDefaults(TenantScope scope) {
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), LibraryOpsCatalog.TYPE_BOOK_CATEGORY)) {
      for (Map<String, Object> m : LibraryOpsCatalog.defaultCategories()) {
        saveSeed(scope, LibraryOpsCatalog.TYPE_BOOK_CATEGORY, m);
      }
    }
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), LibraryOpsCatalog.TYPE_FINE_POLICY)) {
      for (Map<String, Object> m : LibraryOpsCatalog.defaultFinePolicies()) {
        saveSeed(scope, LibraryOpsCatalog.TYPE_FINE_POLICY, m);
      }
    }
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), LibraryOpsCatalog.TYPE_CIRCULATION_POLICY)) {
      for (Map<String, Object> m : LibraryOpsCatalog.defaultCirculationPolicies()) {
        saveSeed(scope, LibraryOpsCatalog.TYPE_CIRCULATION_POLICY, m);
      }
    }
  }

  private void saveSeed(TenantScope scope, String type, Map<String, Object> payload) {
    String key = stringOr(payload.get("definitionKey"), "default");
    OpsDefinitionEntity entity = new OpsDefinitionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setDefinitionType(type);
    entity.setDefinitionKey(key);
    entity.setStatus("ACTIVE");
    entity.setVersion(1);
    entity.setPayload(new LinkedHashMap<>(payload));
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    definitionRepo.save(entity);
  }

  private void requireOps(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, LibraryRecordService.FEATURE_LIBRARY)) {
      throw new LibraryException("FEATURE_OFF", "FEATURE_LIBRARY is off for this subscription plan.");
    }
    if (!engines.isFeatureEnabled(scope, LibraryOpsCatalog.FEATURE_OPS_DEPTH)) {
      throw new LibraryException("FEATURE_OFF", "FEATURE_OPS_DEPTH is off for this subscription plan.");
    }
  }

  private Map<String, Object> requireDefinition(TenantScope scope, String type, String key) {
    OpsDefinitionEntity entity =
        definitionRepo
            .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                scope.organizationId(), type, key, "ACTIVE")
            .orElseThrow(
                () -> new LibraryException("NOT_FOUND", "Ops definition not found: " + type + "/" + key));
    return entity.getPayload() != null ? entity.getPayload() : Map.of();
  }

  private Map<String, Object> toDefinitionDto(OpsDefinitionEntity e) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", e.getId());
    dto.put("definitionType", e.getDefinitionType());
    dto.put("definitionKey", e.getDefinitionKey());
    dto.put("status", e.getStatus());
    dto.put("version", e.getVersion());
    dto.put("payload", e.getPayload());
    if (e.getPayload() != null) {
      e.getPayload().forEach((k, v) -> {
        if (!dto.containsKey(k)) {
          dto.put(k, v);
        }
      });
    }
    return dto;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> moduleSettingsMap(Map<String, Object> module) {
    if (module == null) {
      return Map.of();
    }
    Object settings = module.get("settings");
    if (settings instanceof Map<?, ?> m) {
      return new LinkedHashMap<>((Map<String, Object>) m);
    }
    return new LinkedHashMap<>(module);
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? fallback : s;
  }

  private static int toInt(Object value, int fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return (int) Math.round(Double.parseDouble(String.valueOf(value)));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static BigDecimal toDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return BigDecimal.ZERO;
    }
  }
}
