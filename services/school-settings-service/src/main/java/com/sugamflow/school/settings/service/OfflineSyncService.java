package com.sugamflow.school.settings.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.settings.config.SettingsProperties;
import com.sugamflow.school.settings.integration.SubscriptionClient;
import com.sugamflow.school.settings.model.ModuleSettings;
import com.sugamflow.school.settings.offline.OfflineCatalog;
import com.sugamflow.school.settings.persistence.entity.OfflineSyncBatchEntity;
import com.sugamflow.school.settings.persistence.entity.OfflineSyncItemEntity;
import com.sugamflow.school.settings.persistence.repo.OfflineSyncBatchRepository;
import com.sugamflow.school.settings.persistence.repo.OfflineSyncItemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OfflineSyncService {

  private static final Logger log = LoggerFactory.getLogger(OfflineSyncService.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final SettingsConfigService settings;
  private final SubscriptionClient subscription;
  private final OfflineSyncBatchRepository batchRepo;
  private final OfflineSyncItemRepository itemRepo;
  private final RestClient.Builder restClientBuilder;
  private final SettingsProperties properties;

  public OfflineSyncService(
      SettingsConfigService settings,
      SubscriptionClient subscription,
      OfflineSyncBatchRepository batchRepo,
      OfflineSyncItemRepository itemRepo,
      RestClient.Builder restClientBuilder,
      SettingsProperties properties) {
    this.settings = settings;
    this.subscription = subscription;
    this.batchRepo = batchRepo;
    this.itemRepo = itemRepo;
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  @Transactional
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    boolean enabled = subscription.isFeatureEnabled(scope, OfflineCatalog.FEATURE_OFFLINE_MODE);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", enabled);
    out.put("requiredFeatureFlag", OfflineCatalog.FEATURE_OFFLINE_MODE);
    if (!enabled) {
      return out;
    }
    Map<String, Object> cfg = offlineSettings(scope);
    out.put("moduleKey", OfflineCatalog.MODULE_KEY);
    out.put("maxQueueSize", intOr(cfg.get("maxQueueSize"), 200));
    out.put("syncBatchSize", intOr(cfg.get("syncBatchSize"), 25));
    out.put("autoSyncOnReconnect", boolOr(cfg.get("autoSyncOnReconnect"), true));
    out.put("cacheTtlMinutes", intOr(cfg.get("cacheTtlMinutes"), 1440));
    out.put("allowedEntityTypes", filterEnabledEntities(asListOfMaps(cfg.get("allowedEntityTypes")), scope));
    out.put("cacheKeys", asListOfMaps(cfg.get("cacheKeys")));
    out.put("notes", cfg.get("notes"));
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> manifest() {
    Map<String, Object> boot = bootstrap();
    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("featureEnabled", boot.get("featureEnabled"));
    manifest.put("generatedAt", Instant.now().toString());
    manifest.put("cacheKeys", boot.getOrDefault("cacheKeys", List.of()));
    manifest.put("allowedEntityTypes", boot.getOrDefault("allowedEntityTypes", List.of()));
    return manifest;
  }

  @Transactional
  public Map<String, Object> sync(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    Map<String, Object> cfg = offlineSettings(scope);
    int batchSize = intOr(cfg.get("syncBatchSize"), 25);
    List<Map<String, Object>> allowed = filterEnabledEntities(asListOfMaps(cfg.get("allowedEntityTypes")), scope);
    Map<String, Map<String, Object>> allowByType = new LinkedHashMap<>();
    for (Map<String, Object> a : allowed) {
      allowByType.put(String.valueOf(a.get("entityType")), a);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items =
        body != null && body.get("items") instanceof List<?> list
            ? (List<Map<String, Object>>) (List<?>) castMaps(list)
            : List.of();
    if (items.size() > batchSize) {
      throw new IllegalArgumentException("Batch exceeds syncBatchSize=" + batchSize);
    }

    String batchId = UUID.randomUUID().toString();
    OfflineSyncBatchEntity batch = new OfflineSyncBatchEntity();
    batch.setId(batchId);
    batch.setOrganizationId(scope.organizationId());
    batch.setBranchId(scope.branchId());
    batch.setClientId(body == null ? null : asString(body.get("clientId")));
    batch.setItemCount(items.size());
    batch.setPayloadJson(body != null ? new LinkedHashMap<>(body) : Map.of());
    batch.setCreatedAt(Instant.now());

    int success = 0;
    int failure = 0;
    List<Map<String, Object>> results = new ArrayList<>();

    for (Map<String, Object> item : items) {
      Map<String, Object> itemResult = processItem(scope, batchId, item, allowByType);
      results.add(itemResult);
      if ("SYNCED".equals(itemResult.get("status"))) {
        success++;
      } else {
        failure++;
      }
    }

    batch.setSuccessCount(success);
    batch.setFailureCount(failure);
    batch.setStatus(failure == 0 ? "COMPLETED" : (success == 0 ? "FAILED" : "PARTIAL"));
    Map<String, Object> resultJson = new LinkedHashMap<>();
    resultJson.put("items", results);
    batch.setResultJson(resultJson);
    batchRepo.save(batch);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("batchId", batchId);
    out.put("status", batch.getStatus());
    out.put("itemCount", items.size());
    out.put("successCount", success);
    out.put("failureCount", failure);
    out.put("items", results);
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listBatches() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return batchRepo.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId()).stream()
        .limit(50)
        .map(this::toBatchMap)
        .toList();
  }

  private Map<String, Object> processItem(
      TenantScope scope,
      String batchId,
      Map<String, Object> item,
      Map<String, Map<String, Object>> allowByType) {
    OfflineSyncItemEntity row = new OfflineSyncItemEntity();
    row.setId(UUID.randomUUID().toString());
    row.setBatchId(batchId);
    row.setOrganizationId(scope.organizationId());
    row.setClientItemId(asString(item.get("id")));
    String entityType = asString(item.get("entityType"));
    row.setEntityType(entityType == null ? "UNKNOWN" : entityType);
    row.setRequestJson(new LinkedHashMap<>(item));
    row.setCreatedAt(Instant.now());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("clientItemId", row.getClientItemId());
    result.put("entityType", row.getEntityType());

    Map<String, Object> allow = allowByType.get(row.getEntityType());
    if (allow == null) {
      row.setStatus("REJECTED");
      row.setErrorMessage("Entity type not allow-listed for offline sync");
      itemRepo.save(row);
      result.put("status", "REJECTED");
      result.put("error", row.getErrorMessage());
      return result;
    }

    String method = String.valueOf(allow.getOrDefault("method", "POST")).toUpperCase(Locale.ROOT);
    String path = String.valueOf(allow.get("path"));
    Object body = item.get("body");
    try {
      Map<String, Object> response = forward(scope, method, path, body);
      row.setStatus("SYNCED");
      row.setResponseJson(response != null ? response : Map.of("ok", true));
      itemRepo.save(row);
      result.put("status", "SYNCED");
      result.put("response", row.getResponseJson());
      return result;
    } catch (Exception ex) {
      log.warn("Offline sync item failed: {}", ex.getMessage());
      row.setStatus("FAILED");
      row.setErrorMessage(ex.getMessage());
      itemRepo.save(row);
      result.put("status", "FAILED");
      result.put("error", ex.getMessage());
      return result;
    }
  }

  private Map<String, Object> forward(
      TenantScope scope, String method, String path, Object body) {
    String url = properties.getIntegrations().getPublicApiBaseUrl() + path;
    try {
      var spec =
          restClientBuilder
              .build()
              .method(org.springframework.http.HttpMethod.valueOf(method))
              .uri(url)
              .headers(
                  h -> {
                    TenantHeaders.apply(h, scope);
                    h.set("X-Offline-Sync", "true");
                    String auth = currentAuthorization();
                    if (auth != null && !auth.isBlank()) {
                      h.set("Authorization", auth);
                    }
                  });
      Map<String, Object> envelope;
      if ("GET".equals(method) || "DELETE".equals(method)) {
        envelope = spec.retrieve().body(MAP_TYPE);
      } else {
        envelope =
            spec.contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(MAP_TYPE);
      }
      return unwrap(envelope);
    } catch (RestClientResponseException ex) {
      throw new IllegalStateException(
          "Upstream " + method + " " + path + " failed: " + ex.getStatusCode());
    }
  }

  private static String currentAuthorization() {
    try {
      var attrs =
          org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
      if (attrs
          instanceof
          org.springframework.web.context.request.ServletRequestAttributes servletAttrs) {
        return servletAttrs.getRequest().getHeader("Authorization");
      }
    } catch (Exception ignored) {
      // no request
    }
    return null;
  }

  private void requireFeature(TenantScope scope) {
    if (!subscription.isFeatureEnabled(scope, OfflineCatalog.FEATURE_OFFLINE_MODE)) {
      throw new IllegalStateException(
          "FEATURE_OFFLINE_MODE is off for this subscription plan.");
    }
  }

  private Map<String, Object> offlineSettings(TenantScope scope) {
    ModuleSettings module =
        settings.getModule(OfflineCatalog.MODULE_KEY, scope.organizationId(), scope.branchId());
    return module.getSettings() != null ? module.getSettings() : OfflineCatalog.defaultSettings();
  }

  private List<Map<String, Object>> filterEnabledEntities(
      List<Map<String, Object>> entities, TenantScope scope) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> e : entities) {
      if (Boolean.FALSE.equals(e.get("enabled"))) {
        continue;
      }
      Object flag = e.get("requiredFeatureFlag");
      if (flag != null && !String.valueOf(flag).isBlank()) {
        if (!subscription.isFeatureEnabled(scope, String.valueOf(flag))) {
          continue;
        }
      }
      out.add(e);
    }
    return out;
  }

  private Map<String, Object> toBatchMap(OfflineSyncBatchEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("clientId", e.getClientId());
    m.put("status", e.getStatus());
    m.put("itemCount", e.getItemCount());
    m.put("successCount", e.getSuccessCount());
    m.put("failureCount", e.getFailureCount());
    m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
    return m;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> unwrap(Map<String, Object> envelope) {
    if (envelope == null) {
      return Map.of();
    }
    Object data = envelope.get("data");
    if (data instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    if (data == null) {
      return envelope;
    }
    return Map.of("value", data);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> asListOfMaps(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m) {
        out.add((Map<String, Object>) m);
      }
    }
    return out;
  }

  private static List<Map<String, Object>> castMaps(List<?> list) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) m;
        out.add(map);
      }
    }
    return out;
  }

  private static String asString(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static int intOr(Object v, int d) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return v != null ? Integer.parseInt(String.valueOf(v)) : d;
    } catch (NumberFormatException ex) {
      return d;
    }
  }

  private static boolean boolOr(Object v, boolean d) {
    if (v instanceof Boolean b) {
      return b;
    }
    if (v != null) {
      return "true".equalsIgnoreCase(String.valueOf(v));
    }
    return d;
  }
}
