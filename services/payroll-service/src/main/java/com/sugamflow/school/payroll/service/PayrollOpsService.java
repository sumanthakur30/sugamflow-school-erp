package com.sugamflow.school.payroll.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.payroll.integration.ConfigEngineClient;
import com.sugamflow.school.payroll.ops.PayrollOpsCatalog;
import com.sugamflow.school.payroll.persistence.entity.OpsDefinitionEntity;
import com.sugamflow.school.payroll.persistence.repo.OpsDefinitionRepository;
import com.sugamflow.school.payroll.web.PayrollException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollOpsService {

  private final OpsDefinitionRepository definitionRepo;
  private final ConfigEngineClient engines;

  public PayrollOpsService(OpsDefinitionRepository definitionRepo, ConfigEngineClient engines) {
    this.definitionRepo = definitionRepo;
    this.engines = engines;
  }

  @Transactional
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    ensureDefaults(scope);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("components", listDefinitions(PayrollOpsCatalog.TYPE_PAY_COMPONENT));
    out.put("structures", listDefinitions(PayrollOpsCatalog.TYPE_SALARY_STRUCTURE));
    out.put("payCycles", listDefinitions(PayrollOpsCatalog.TYPE_PAY_CYCLE));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listDefinitions(String type) {
    requireOps(TenantContext.require());
    return definitionRepo
        .findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
            TenantContext.require().organizationId(), type, "ACTIVE")
        .stream()
        .map(this::toDefinitionDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> saveDefinition(String type, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    String key = stringOr(body.get("definitionKey"), null);
    if (key == null || key.isBlank()) {
      throw new PayrollException("VALIDATION", "definitionKey is required");
    }
    key = key.trim().toLowerCase().replace(' ', '_');
    return saveVersioned(scope, type, key, body);
  }

  @Transactional
  public Map<String, Object> previewPayslip(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    ensureDefaults(scope);
    Map<String, Object> settings =
        moduleSettingsMap(engines.getModuleSettings(scope, PayrollRecordService.MODULE_PAYROLL));
    String structureKey =
        stringOr(body.get("structureKey"), stringOr(settings.get("defaultSalaryStructureKey"), "default_staff"));
    Map<String, Object> structure =
        requireDefinition(scope, PayrollOpsCatalog.TYPE_SALARY_STRUCTURE, structureKey);
    BigDecimal basic = toDecimal(body.get("basicPay"));
    if (basic.compareTo(BigDecimal.ZERO) <= 0) {
      basic = new BigDecimal("25000");
    }
    List<Map<String, Object>> earnings = new ArrayList<>();
    List<Map<String, Object>> deductions = new ArrayList<>();
    earnings.add(line("BASIC", basic, "EARNING"));
    BigDecimal gross = basic;
    BigDecimal totalDeductions = BigDecimal.ZERO;
    Object rawLines = structure.get("lines");
    if (rawLines instanceof List<?> list) {
      for (Object item : list) {
        if (!(item instanceof Map<?, ?> m)) {
          continue;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> line = (Map<String, Object>) m;
        String componentKey = stringOr(line.get("componentKey"), "");
        String calcType = stringOr(line.get("calcType"), "");
        BigDecimal value = toDecimal(line.get("value"));
        BigDecimal amount = BigDecimal.ZERO;
        if ("PERCENT_OF_BASIC".equalsIgnoreCase(calcType)) {
          amount = basic.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
          amount = value;
        }
        Map<String, Object> component = requireDefinition(scope, PayrollOpsCatalog.TYPE_PAY_COMPONENT, componentKey);
        String kind = stringOr(component.get("kind"), "EARNING");
        if ("DEDUCTION".equalsIgnoreCase(kind)) {
          deductions.add(line(componentKey, amount, kind));
          totalDeductions = totalDeductions.add(amount);
        } else {
          earnings.add(line(componentKey, amount, kind));
          gross = gross.add(amount);
        }
      }
    }
    BigDecimal net = gross.subtract(totalDeductions);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("structureKey", structureKey);
    out.put("basicPay", basic.setScale(2, RoundingMode.HALF_UP));
    out.put("grossPay", gross.setScale(2, RoundingMode.HALF_UP));
    out.put("totalDeductions", totalDeductions.setScale(2, RoundingMode.HALF_UP));
    out.put("netPay", net.setScale(2, RoundingMode.HALF_UP));
    out.put("earnings", earnings);
    out.put("deductions", deductions);
    out.put("currency", stringOr(structure.get("currency"), "INR"));
    return out;
  }

  @Transactional
  public void ensureDefaults(TenantScope scope) {
    seedIfMissing(scope, PayrollOpsCatalog.TYPE_PAY_COMPONENT, PayrollOpsCatalog.defaultComponents());
    seedIfMissing(scope, PayrollOpsCatalog.TYPE_SALARY_STRUCTURE, PayrollOpsCatalog.defaultStructures());
    seedIfMissing(scope, PayrollOpsCatalog.TYPE_PAY_CYCLE, PayrollOpsCatalog.defaultPayCycles());
  }

  private static Map<String, Object> line(String key, BigDecimal amount, String kind) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("componentKey", key);
    m.put("amount", amount.setScale(2, RoundingMode.HALF_UP));
    m.put("kind", kind);
    return m;
  }

  private void seedIfMissing(TenantScope scope, String type, List<Map<String, Object>> seeds) {
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(scope.organizationId(), type)) {
      for (Map<String, Object> m : seeds) {
        saveSeed(scope, type, m);
      }
    }
  }

  private Map<String, Object> saveVersioned(
      TenantScope scope, String type, String key, Map<String, Object> body) {
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
    if (!engines.isFeatureEnabled(scope, PayrollRecordService.FEATURE_PAYROLL)) {
      throw new PayrollException("FEATURE_OFF", "FEATURE_PAYROLL is off for this subscription plan.");
    }
    if (!engines.isFeatureEnabled(scope, PayrollOpsCatalog.FEATURE_OPS_DEPTH)) {
      throw new PayrollException("FEATURE_OFF", "FEATURE_OPS_DEPTH is off for this subscription plan.");
    }
  }

  private Map<String, Object> requireDefinition(TenantScope scope, String type, String key) {
    return definitionRepo
        .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
            scope.organizationId(), type, key, "ACTIVE")
        .map(e -> e.getPayload() != null ? e.getPayload() : Map.<String, Object>of())
        .orElseThrow(
            () -> new PayrollException("NOT_FOUND", "Ops definition not found: " + type + "/" + key));
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
