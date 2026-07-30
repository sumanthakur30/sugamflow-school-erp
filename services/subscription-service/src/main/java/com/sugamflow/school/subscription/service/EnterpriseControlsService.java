package com.sugamflow.school.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.persistence.entity.EnterpriseAuditEventEntity;
import com.sugamflow.school.subscription.persistence.entity.EnterpriseAuditExportEntity;
import com.sugamflow.school.subscription.persistence.entity.EnterpriseOrgSettingsEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.repo.EnterpriseAuditEventRepository;
import com.sugamflow.school.subscription.persistence.repo.EnterpriseAuditExportRepository;
import com.sugamflow.school.subscription.persistence.repo.EnterpriseOrgSettingsRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 14 enterprise controls: SSO / white-label / residency / HA config + audit export. Settings
 * are configuration-only; runtime still enforces via feature flags / settings services. Entitlements
 * JSON contracts unchanged.
 */
@Service
public class EnterpriseControlsService {

  private static final Set<String> SSO_PROVIDERS = Set.of("OIDC", "SAML", "NONE");
  private static final Set<String> RESIDENCY = Set.of("IN", "EU", "US", "APAC", "CUSTOM");

  private final EnterpriseOrgSettingsRepository settingsRepository;
  private final EnterpriseAuditEventRepository auditEventRepository;
  private final EnterpriseAuditExportRepository exportRepository;
  private final TenantSubscriptionRepository tenantSubscriptionRepository;
  private final SubscriptionService subscriptionService;
  private final ObjectMapper objectMapper;

  public EnterpriseControlsService(
      EnterpriseOrgSettingsRepository settingsRepository,
      EnterpriseAuditEventRepository auditEventRepository,
      EnterpriseAuditExportRepository exportRepository,
      TenantSubscriptionRepository tenantSubscriptionRepository,
      SubscriptionService subscriptionService,
      ObjectMapper objectMapper) {
    this.settingsRepository = settingsRepository;
    this.auditEventRepository = auditEventRepository;
    this.exportRepository = exportRepository;
    this.tenantSubscriptionRepository = tenantSubscriptionRepository;
    this.subscriptionService = subscriptionService;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> getSettings(String organizationId) {
    EnterpriseOrgSettingsEntity row =
        settingsRepository.findById(organizationId).orElseGet(() -> emptySettings(organizationId));
    Map<String, Object> out = settingsToMap(row);
    out.put("entitlements", entitlementSnapshot(organizationId));
    return out;
  }

  @Transactional
  public Map<String, Object> upsertSettings(String organizationId, Map<String, Object> body) {
    Map<String, Boolean> entitled = entitlementSnapshot(organizationId);
    EnterpriseOrgSettingsEntity row =
        settingsRepository.findById(organizationId).orElseGet(() -> emptySettings(organizationId));
    Map<String, Object> before = settingsToMap(row);

    if (body.containsKey("ssoEnabled") || body.containsKey("ssoProvider") || body.containsKey("ssoConfig")) {
      if (!Boolean.TRUE.equals(entitled.get("sso"))) {
        throw new IllegalArgumentException(
            "SSO not entitled for this plan (need FEATURE_SSO or FEATURE_WHITE_LABEL)");
      }
    }
    if (body.containsKey("whiteLabelEnabled") || body.containsKey("whiteLabel")) {
      if (!Boolean.TRUE.equals(entitled.get("whiteLabel"))) {
        throw new IllegalArgumentException("White-label not entitled (need FEATURE_WHITE_LABEL)");
      }
    }

    if (body.containsKey("ssoEnabled")) {
      row.setSsoEnabled(asBool(body.get("ssoEnabled"), false));
    }
    if (body.containsKey("ssoProvider")) {
      String provider = str(body.get("ssoProvider"));
      if (provider != null && !provider.isBlank()) {
        provider = provider.toUpperCase(Locale.ROOT);
        if (!SSO_PROVIDERS.contains(provider)) {
          throw new IllegalArgumentException("Invalid ssoProvider: " + provider);
        }
        row.setSsoProvider("NONE".equals(provider) ? null : provider);
      } else {
        row.setSsoProvider(null);
      }
    }
    if (body.containsKey("ssoConfig")) {
      row.setSsoConfigJson(toObjectMap(body.get("ssoConfig")));
    }
    if (body.containsKey("whiteLabelEnabled")) {
      row.setWhiteLabelEnabled(asBool(body.get("whiteLabelEnabled"), false));
    }
    if (body.containsKey("whiteLabel")) {
      row.setWhiteLabelJson(toObjectMap(body.get("whiteLabel")));
    }
    if (body.containsKey("residencyRegion")) {
      String region = str(body.get("residencyRegion"));
      if (region == null || region.isBlank()) {
        row.setResidencyRegion(null);
      } else {
        region = region.toUpperCase(Locale.ROOT);
        if (!RESIDENCY.contains(region)) {
          throw new IllegalArgumentException("Invalid residencyRegion: " + region);
        }
        row.setResidencyRegion(region);
      }
    }
    if (body.containsKey("haOptions")) {
      row.setHaOptionsJson(toObjectMap(body.get("haOptions")));
    }
    if (body.containsKey("notes")) {
      row.setNotes(str(body.get("notes")));
    }
    Instant now = Instant.now();
    if (row.getCreatedAt() == null) {
      row.setCreatedAt(now);
    }
    row.setOrganizationId(organizationId);
    row.setUpdatedAt(now);
    settingsRepository.save(row);

    Map<String, Object> after = settingsToMap(row);
    recordEvent(
        organizationId,
        "ENTERPRISE_SETTINGS_UPDATED",
        strOr(body.get("actor"), "admin"),
        Map.of("before", before, "after", after));

    Map<String, Object> out = after;
    out.put("entitlements", entitled);
    return out;
  }

  public List<Map<String, Object>> listAuditEvents(String organizationId, int limit) {
    int top = Math.max(1, Math.min(limit, 500));
    return auditEventRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .limit(top)
        .map(this::eventToMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> createAuditExport(String organizationId, Map<String, Object> body) {
    Instant fromAt = parseInstant(body == null ? null : body.get("fromAt"));
    Instant toAt = parseInstant(body == null ? null : body.get("toAt"));
    String format =
        strOr(body == null ? null : body.get("format"), "CSV").toUpperCase(Locale.ROOT);
    if (!Set.of("CSV", "JSON").contains(format)) {
      throw new IllegalArgumentException("format must be CSV or JSON");
    }

    List<EnterpriseAuditEventEntity> events =
        auditEventRepository.findForExport(organizationId, fromAt, toAt);

    EnterpriseAuditExportEntity export = new EnterpriseAuditExportEntity();
    export.setOrganizationId(organizationId);
    export.setFromAt(fromAt);
    export.setToAt(toAt);
    export.setFormat(format);
    export.setRequestedBy(strOr(body == null ? null : body.get("requestedBy"), "admin"));
    export.setCreatedAt(Instant.now());
    try {
      if ("JSON".equals(format)) {
        List<Map<String, Object>> rows = events.stream().map(this::eventToMap).toList();
        export.setContentText(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
      } else {
        export.setContentText(toCsv(events));
      }
      export.setRowCount(events.size());
      export.setStatus("READY");
    } catch (Exception ex) {
      export.setStatus("FAILED");
      export.setContentText("Export failed: " + ex.getMessage());
      export.setRowCount(0);
    }
    exportRepository.save(export);

    recordEvent(
        organizationId,
        "ENTERPRISE_AUDIT_EXPORT",
        export.getRequestedBy(),
        Map.of("exportId", export.getId(), "rowCount", export.getRowCount(), "format", format));

    return exportToMap(export, true);
  }

  public List<Map<String, Object>> listExports(String organizationId) {
    return exportRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(e -> exportToMap(e, false))
        .toList();
  }

  public Map<String, Object> getExport(String organizationId, long exportId) {
    EnterpriseAuditExportEntity export =
        exportRepository
            .findById(exportId)
            .filter(e -> organizationId.equals(e.getOrganizationId()))
            .orElseThrow(() -> new IllegalArgumentException("Export not found: " + exportId));
    return exportToMap(export, true);
  }

  public Map<String, Object> dashboard() {
    long orgsWithSso =
        settingsRepository.findAll().stream().filter(EnterpriseOrgSettingsEntity::isSsoEnabled).count();
    long orgsWithWl =
        settingsRepository.findAll().stream()
            .filter(EnterpriseOrgSettingsEntity::isWhiteLabelEnabled)
            .count();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("configuredOrgs", settingsRepository.count());
    out.put("ssoEnabledOrgs", orgsWithSso);
    out.put("whiteLabelEnabledOrgs", orgsWithWl);
    out.put("auditEvents", auditEventRepository.count());
    out.put("exports", exportRepository.count());
    return out;
  }

  private Map<String, Boolean> entitlementSnapshot(String organizationId) {
    String planId =
        tenantSubscriptionRepository
            .findById(organizationId)
            .map(TenantSubscriptionEntity::getPlanId)
            .orElse("starter");
    SubscriptionPlan plan = subscriptionService.getPlan(planId);
    Map<String, Boolean> flags =
        plan == null || plan.getFeatureFlags() == null ? Map.of() : plan.getFeatureFlags();
    boolean whiteLabel = Boolean.TRUE.equals(flags.get("FEATURE_WHITE_LABEL"));
    boolean sso =
        Boolean.TRUE.equals(flags.get("FEATURE_SSO"))
            || whiteLabel
            || Boolean.TRUE.equals(flags.get("FEATURE_CUSTOM_DOMAIN"));
    boolean customDomain = Boolean.TRUE.equals(flags.get("FEATURE_CUSTOM_DOMAIN")) || whiteLabel;
    return Map.of(
        "whiteLabel", whiteLabel,
        "sso", sso,
        "customDomain", customDomain);
  }

  private void recordEvent(
      String organizationId, String type, String actor, Map<String, Object> detail) {
    EnterpriseAuditEventEntity event = new EnterpriseAuditEventEntity();
    event.setOrganizationId(organizationId);
    event.setEventType(type);
    event.setActor(actor);
    event.setDetailJson(new LinkedHashMap<>(detail));
    event.setCreatedAt(Instant.now());
    auditEventRepository.save(event);
  }

  private EnterpriseOrgSettingsEntity emptySettings(String organizationId) {
    EnterpriseOrgSettingsEntity row = new EnterpriseOrgSettingsEntity();
    row.setOrganizationId(organizationId);
    row.setSsoEnabled(false);
    row.setWhiteLabelEnabled(false);
    row.setSsoConfigJson(new LinkedHashMap<>());
    row.setWhiteLabelJson(new LinkedHashMap<>());
    row.setHaOptionsJson(new LinkedHashMap<>());
    row.setCreatedAt(Instant.now());
    row.setUpdatedAt(Instant.now());
    return row;
  }

  private String toCsv(List<EnterpriseAuditEventEntity> events) {
    StringBuilder sb = new StringBuilder();
    sb.append("id,organizationId,eventType,actor,createdAt,detailJson\n");
    for (EnterpriseAuditEventEntity e : events) {
      sb.append(e.getId())
          .append(',')
          .append(csv(e.getOrganizationId()))
          .append(',')
          .append(csv(e.getEventType()))
          .append(',')
          .append(csv(e.getActor()))
          .append(',')
          .append(csv(e.getCreatedAt() == null ? "" : e.getCreatedAt().toString()))
          .append(',')
          .append(csv(stringify(e.getDetailJson())))
          .append('\n');
    }
    return sb.toString();
  }

  private String stringify(Object o) {
    try {
      return objectMapper.writeValueAsString(o);
    } catch (Exception ex) {
      return String.valueOf(o);
    }
  }

  private static String csv(String v) {
    if (v == null) {
      return "";
    }
    String escaped = v.replace("\"", "\"\"");
    return '"' + escaped + '"';
  }

  private Map<String, Object> settingsToMap(EnterpriseOrgSettingsEntity row) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("organizationId", row.getOrganizationId());
    map.put("ssoEnabled", row.isSsoEnabled());
    map.put("ssoProvider", row.getSsoProvider());
    map.put("ssoConfig", row.getSsoConfigJson());
    map.put("whiteLabelEnabled", row.isWhiteLabelEnabled());
    map.put("whiteLabel", row.getWhiteLabelJson());
    map.put("residencyRegion", row.getResidencyRegion());
    map.put("haOptions", row.getHaOptionsJson());
    map.put("notes", row.getNotes());
    map.put("updatedAt", row.getUpdatedAt());
    map.put("createdAt", row.getCreatedAt());
    return map;
  }

  private Map<String, Object> eventToMap(EnterpriseAuditEventEntity e) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", e.getId());
    map.put("organizationId", e.getOrganizationId());
    map.put("eventType", e.getEventType());
    map.put("actor", e.getActor());
    map.put("detail", e.getDetailJson());
    map.put("createdAt", e.getCreatedAt());
    return map;
  }

  private Map<String, Object> exportToMap(EnterpriseAuditExportEntity e, boolean includeContent) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", e.getId());
    map.put("organizationId", e.getOrganizationId());
    map.put("status", e.getStatus());
    map.put("fromAt", e.getFromAt());
    map.put("toAt", e.getToAt());
    map.put("format", e.getFormat());
    map.put("rowCount", e.getRowCount());
    map.put("requestedBy", e.getRequestedBy());
    map.put("createdAt", e.getCreatedAt());
    if (includeContent) {
      map.put("content", e.getContentText());
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toObjectMap(Object raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (raw instanceof Map<?, ?> map) {
      map.forEach((k, v) -> out.put(String.valueOf(k), v));
    }
    return out;
  }

  private static Instant parseInstant(Object v) {
    if (v == null || String.valueOf(v).isBlank()) {
      return null;
    }
    return Instant.parse(String.valueOf(v).trim());
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v).trim();
  }

  private static String strOr(Object v, String fallback) {
    String s = str(v);
    return s == null || s.isBlank() ? fallback : s;
  }

  private static boolean asBool(Object v, boolean fallback) {
    if (v == null) {
      return fallback;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }
}
