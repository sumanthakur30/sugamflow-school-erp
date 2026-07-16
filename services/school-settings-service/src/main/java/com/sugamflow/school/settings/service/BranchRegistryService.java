package com.sugamflow.school.settings.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.integration.AuditClient;
import com.sugamflow.school.settings.integration.SubscriptionClient;
import com.sugamflow.school.settings.persistence.entity.OrgBranchEntity;
import com.sugamflow.school.settings.persistence.repo.OrgBranchRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchRegistryService {

  public static final String FEATURE_MULTI_BRANCH = "FEATURE_MULTI_BRANCH";

  private final OrgBranchRepository repo;
  private final SubscriptionClient subscription;
  private final AuditClient auditClient;

  public BranchRegistryService(
      OrgBranchRepository repo, SubscriptionClient subscription, AuditClient auditClient) {
    this.repo = repo;
    this.subscription = subscription;
    this.auditClient = auditClient;
  }

  @Transactional
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    ensureDefaultBranch(scope.organizationId());
    boolean multiEnabled = subscription.isFeatureEnabled(scope, FEATURE_MULTI_BRANCH);
    long maxBranches = subscription.getMaxBranches(scope);
    List<Map<String, Object>> branches = list(scope.organizationId());
    String currentKey =
        scope.branchId() == null || scope.branchId().isBlank() ? "main" : scope.branchId();
    Map<String, Object> current =
        branches.stream()
            .filter(b -> currentKey.equals(b.get("branchKey")))
            .findFirst()
            .orElse(branches.isEmpty() ? null : branches.get(0));

    boolean unlimited = maxBranches < 0;
    boolean canAdd =
        multiEnabled && (unlimited || branches.size() < maxBranches);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", multiEnabled);
    out.put("requiredFeatureFlag", FEATURE_MULTI_BRANCH);
    out.put("maxBranches", maxBranches);
    out.put("branchCount", branches.size());
    out.put("canAdd", canAdd);
    out.put("currentBranchKey", current != null ? current.get("branchKey") : currentKey);
    out.put("currentBranch", current);
    out.put("branches", branches);
    return out;
  }

  @Transactional
  public List<Map<String, Object>> list(String org) {
    ensureDefaultBranch(org);
    List<OrgBranchEntity> entities = new ArrayList<>(repo.findByOrganizationIdOrderByNameAsc(org));
    entities.sort(
        (a, b) -> {
          if (a.isDefault() == b.isDefault()) {
            return a.getName().compareToIgnoreCase(b.getName());
          }
          return a.isDefault() ? -1 : 1;
        });
    List<Map<String, Object>> out = new ArrayList<>();
    for (OrgBranchEntity e : entities) {
      out.add(toMap(e));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(String org, String branchKey) {
    return repo.findByOrganizationIdAndBranchKey(org, branchKey).map(this::toMap).orElse(null);
  }

  @Transactional
  public Map<String, Object> create(String org, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    ensureDefaultBranch(org);
    if (!subscription.isFeatureEnabled(scope, FEATURE_MULTI_BRANCH)) {
      throw new IllegalStateException(
          "FEATURE_MULTI_BRANCH is off for this subscription plan.");
    }
    long max = subscription.getMaxBranches(scope);
    long count = repo.countByOrganizationId(org);
    if (max >= 0 && count >= max) {
      throw new IllegalStateException(
          "Branch limit reached (maxBranches=" + max + "). Upgrade the plan to add campuses.");
    }
    String key = normalizeKey(asString(body.get("branchKey")));
    if (key == null || key.isBlank()) {
      key = normalizeKey(asString(body.get("code")));
    }
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("branchKey is required");
    }
    if (repo.findByOrganizationIdAndBranchKey(org, key).isPresent()) {
      throw new IllegalArgumentException("Branch already exists: " + key);
    }
    OrgBranchEntity e = new OrgBranchEntity();
    e.setOrganizationId(org);
    e.setBranchKey(key);
    applyBody(e, body, true);
    if (e.getName() == null || e.getName().isBlank()) {
      e.setName(key);
    }
    e.setUpdatedAt(Instant.now());
    if (e.isDefault()) {
      clearDefaults(org);
    }
    Map<String, Object> saved = toMap(repo.save(e));
    auditClient.recordChange("ORG_BRANCH", key, null, saved, "Branch created: " + key);
    return saved;
  }

  @Transactional
  public Map<String, Object> update(String org, String branchKey, Map<String, Object> body) {
    OrgBranchEntity e =
        repo.findByOrganizationIdAndBranchKey(org, branchKey)
            .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchKey));
    Map<String, Object> before = toMap(e);
    applyBody(e, body, false);
    e.setUpdatedAt(Instant.now());
    if (e.isDefault()) {
      clearDefaults(org);
      e.setDefault(true);
    }
    Map<String, Object> saved = toMap(repo.save(e));
    auditClient.recordChange(
        "ORG_BRANCH", branchKey, before, saved, "Branch updated: " + branchKey);
    return saved;
  }

  private void ensureDefaultBranch(String org) {
    if (repo.countByOrganizationId(org) > 0) {
      return;
    }
    OrgBranchEntity main = new OrgBranchEntity();
    main.setOrganizationId(org);
    main.setBranchKey("main");
    main.setName("Main Campus");
    main.setCode("MAIN");
    main.setCity("");
    main.setStatus("ACTIVE");
    main.setDefault(true);
    main.setPayload(Map.of("notes", "Default campus — configure without code changes"));
    main.setUpdatedAt(Instant.now());
    repo.save(main);
  }

  private void clearDefaults(String org) {
    for (OrgBranchEntity e : repo.findDefaults(org)) {
      e.setDefault(false);
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    }
  }

  private void applyBody(OrgBranchEntity e, Map<String, Object> body, boolean creating) {
    if (body == null) {
      return;
    }
    if (body.get("name") != null) {
      e.setName(String.valueOf(body.get("name")).trim());
    }
    if (body.get("code") != null) {
      e.setCode(String.valueOf(body.get("code")).trim());
    } else if (creating && (e.getCode() == null || e.getCode().isBlank())) {
      e.setCode(e.getBranchKey().toUpperCase(Locale.ROOT));
    }
    if (body.get("city") != null) {
      e.setCity(String.valueOf(body.get("city")).trim());
    }
    if (body.get("address") != null) {
      e.setAddress(String.valueOf(body.get("address")).trim());
    }
    if (body.get("status") != null) {
      e.setStatus(String.valueOf(body.get("status")).trim().toUpperCase(Locale.ROOT));
    } else if (creating) {
      e.setStatus("ACTIVE");
    }
    if (body.get("isDefault") != null) {
      e.setDefault(Boolean.TRUE.equals(body.get("isDefault")) || "true".equalsIgnoreCase(String.valueOf(body.get("isDefault"))));
    }
    if (body.get("payload") instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> payload = (Map<String, Object>) m;
      e.setPayload(new LinkedHashMap<>(payload));
    }
  }

  private static String normalizeKey(String raw) {
    if (raw == null) {
      return null;
    }
    String key =
        raw.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]+", "-")
            .replaceAll("-+", "-");
    if (key.startsWith("-")) {
      key = key.substring(1);
    }
    if (key.endsWith("-")) {
      key = key.substring(0, key.length() - 1);
    }
    return key;
  }

  private static String asString(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private Map<String, Object> toMap(OrgBranchEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("organizationId", e.getOrganizationId());
    m.put("branchKey", e.getBranchKey());
    m.put("name", e.getName());
    m.put("code", e.getCode());
    m.put("city", e.getCity());
    m.put("address", e.getAddress());
    m.put("status", e.getStatus());
    m.put("isDefault", e.isDefault());
    m.put("payload", e.getPayload());
    m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
    return m;
  }
}
