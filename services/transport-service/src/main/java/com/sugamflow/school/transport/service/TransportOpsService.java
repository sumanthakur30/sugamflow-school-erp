package com.sugamflow.school.transport.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.transport.integration.ConfigEngineClient;
import com.sugamflow.school.transport.ops.TransportOpsCatalog;
import com.sugamflow.school.transport.persistence.entity.OpsDefinitionEntity;
import com.sugamflow.school.transport.persistence.repo.OpsDefinitionRepository;
import com.sugamflow.school.transport.web.TransportException;
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
public class TransportOpsService {

  private final OpsDefinitionRepository definitionRepo;
  private final ConfigEngineClient engines;

  public TransportOpsService(OpsDefinitionRepository definitionRepo, ConfigEngineClient engines) {
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
    out.put("routes", listDefinitions(TransportOpsCatalog.TYPE_ROUTE));
    out.put("fareSlabs", listDefinitions(TransportOpsCatalog.TYPE_FARE_SLAB));
    out.put("vehicles", listDefinitions(TransportOpsCatalog.TYPE_VEHICLE));
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
      throw new TransportException("VALIDATION", "definitionKey is required");
    }
    key = key.trim().toLowerCase().replace(' ', '_');
    return saveVersioned(scope, type, key, body);
  }

  @Transactional
  public Map<String, Object> previewFare(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    ensureDefaults(scope);
    Map<String, Object> settings =
        moduleSettingsMap(engines.getModuleSettings(scope, TransportRecordService.MODULE_TRANSPORT));
    String slabKey =
        stringOr(body.get("fareSlabKey"), stringOr(settings.get("defaultFareSlabKey"), "default_fare"));
    String routeKey = stringOr(body.get("routeKey"), stringOr(settings.get("defaultRouteKey"), "route_a"));
    Map<String, Object> slab = requireDefinition(scope, TransportOpsCatalog.TYPE_FARE_SLAB, slabKey);
    Map<String, Object> route = requireDefinition(scope, TransportOpsCatalog.TYPE_ROUTE, routeKey);
    BigDecimal distance =
        body.get("distanceKm") != null ? toDecimal(body.get("distanceKm")) : toDecimal(route.get("distanceKm"));
    BigDecimal base = toDecimal(slab.get("baseAmount"));
    BigDecimal perKm = toDecimal(slab.get("perKmRate"));
    BigDecimal fare = base.add(perKm.multiply(distance));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("routeKey", routeKey);
    out.put("fareSlabKey", slabKey);
    out.put("distanceKm", distance);
    out.put("fareAmount", fare.setScale(2, RoundingMode.HALF_UP));
    out.put("currency", stringOr(slab.get("currency"), "INR"));
    return out;
  }

  @Transactional
  public void ensureDefaults(TenantScope scope) {
    seedIfMissing(scope, TransportOpsCatalog.TYPE_ROUTE, TransportOpsCatalog.defaultRoutes());
    seedIfMissing(scope, TransportOpsCatalog.TYPE_FARE_SLAB, TransportOpsCatalog.defaultFareSlabs());
    seedIfMissing(scope, TransportOpsCatalog.TYPE_VEHICLE, TransportOpsCatalog.defaultVehicles());
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
    if (!engines.isFeatureEnabled(scope, TransportRecordService.FEATURE_TRANSPORT)) {
      throw new TransportException("FEATURE_OFF", "FEATURE_TRANSPORT is off for this subscription plan.");
    }
    if (!engines.isFeatureEnabled(scope, TransportOpsCatalog.FEATURE_OPS_DEPTH)) {
      throw new TransportException("FEATURE_OFF", "FEATURE_OPS_DEPTH is off for this subscription plan.");
    }
  }

  private Map<String, Object> requireDefinition(TenantScope scope, String type, String key) {
    return definitionRepo
        .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
            scope.organizationId(), type, key, "ACTIVE")
        .map(e -> e.getPayload() != null ? e.getPayload() : Map.<String, Object>of())
        .orElseThrow(
            () ->
                new TransportException("NOT_FOUND", "Ops definition not found: " + type + "/" + key));
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
