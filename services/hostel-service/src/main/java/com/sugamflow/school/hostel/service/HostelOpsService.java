package com.sugamflow.school.hostel.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.hostel.integration.ConfigEngineClient;
import com.sugamflow.school.hostel.ops.HostelOpsCatalog;
import com.sugamflow.school.hostel.persistence.entity.OpsDefinitionEntity;
import com.sugamflow.school.hostel.persistence.repo.OpsDefinitionRepository;
import com.sugamflow.school.hostel.web.HostelException;
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
public class HostelOpsService {

  private final OpsDefinitionRepository definitionRepo;
  private final ConfigEngineClient engines;

  public HostelOpsService(OpsDefinitionRepository definitionRepo, ConfigEngineClient engines) {
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
    out.put("blocks", listDefinitions(HostelOpsCatalog.TYPE_BLOCK));
    out.put("roomTypes", listDefinitions(HostelOpsCatalog.TYPE_ROOM_TYPE));
    out.put("allocationPolicies", listDefinitions(HostelOpsCatalog.TYPE_ALLOCATION_POLICY));
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
      throw new HostelException("VALIDATION", "definitionKey is required");
    }
    key = key.trim().toLowerCase().replace(' ', '_');
    return saveVersioned(scope, type, key, body);
  }

  @Transactional
  public Map<String, Object> previewAllocation(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireOps(scope);
    ensureDefaults(scope);
    Map<String, Object> settings = moduleSettingsMap(engines.getModuleSettings(scope, HostelRecordService.MODULE_HOSTEL));
    String roomTypeKey =
        stringOr(body.get("roomTypeKey"), stringOr(settings.get("defaultRoomTypeKey"), "twin_sharing"));
    String policyKey =
        stringOr(
            body.get("allocationPolicyKey"),
            stringOr(settings.get("defaultAllocationPolicyKey"), "default_alloc"));
    Map<String, Object> roomType = requireDefinition(scope, HostelOpsCatalog.TYPE_ROOM_TYPE, roomTypeKey);
    Map<String, Object> policy = requireDefinition(scope, HostelOpsCatalog.TYPE_ALLOCATION_POLICY, policyKey);
    int bedsPerRoom = toInt(roomType.get("bedsPerRoom"), 1);
    int totalRooms = toInt(roomType.get("totalRooms"), 0);
    int totalBeds = bedsPerRoom * totalRooms;
    int maxPct = toInt(policy.get("maxOccupancyPercent"), 100);
    int occupiedBeds = toInt(body.get("occupiedBeds"), 0);
    int bedsRequested = toInt(body.get("bedsRequested"), 1);
    int capacityBeds = (int) Math.floor(totalBeds * maxPct / 100.0);
    int availableBeds = Math.max(0, capacityBeds - occupiedBeds);
    boolean canAllocate = availableBeds >= bedsRequested;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("roomTypeKey", roomTypeKey);
    out.put("allocationPolicyKey", policyKey);
    out.put("totalBeds", totalBeds);
    out.put("capacityBeds", capacityBeds);
    out.put("availableBeds", availableBeds);
    out.put("bedsRequested", bedsRequested);
    out.put("canAllocate", canAllocate);
    out.put("monthlyFee", toDecimal(roomType.get("monthlyFee")));
    return out;
  }

  @Transactional
  public void ensureDefaults(TenantScope scope) {
    seedIfMissing(scope, HostelOpsCatalog.TYPE_BLOCK, HostelOpsCatalog.defaultBlocks());
    seedIfMissing(scope, HostelOpsCatalog.TYPE_ROOM_TYPE, HostelOpsCatalog.defaultRoomTypes());
    seedIfMissing(scope, HostelOpsCatalog.TYPE_ALLOCATION_POLICY, HostelOpsCatalog.defaultAllocationPolicies());
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
    if (!engines.isFeatureEnabled(scope, HostelRecordService.FEATURE_HOSTEL)) {
      throw new HostelException("FEATURE_OFF", "FEATURE_HOSTEL is off for this subscription plan.");
    }
    if (!engines.isFeatureEnabled(scope, HostelOpsCatalog.FEATURE_OPS_DEPTH)) {
      throw new HostelException("FEATURE_OFF", "FEATURE_OPS_DEPTH is off for this subscription plan.");
    }
  }

  private Map<String, Object> requireDefinition(TenantScope scope, String type, String key) {
    return definitionRepo
        .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
            scope.organizationId(), type, key, "ACTIVE")
        .map(e -> e.getPayload() != null ? e.getPayload() : Map.<String, Object>of())
        .orElseThrow(
            () -> new HostelException("NOT_FOUND", "Ops definition not found: " + type + "/" + key));
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
      return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    } catch (NumberFormatException ex) {
      return BigDecimal.ZERO;
    }
  }
}
