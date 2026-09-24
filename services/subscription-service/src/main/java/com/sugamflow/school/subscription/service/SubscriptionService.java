package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.cache.SubscriptionCacheSupport;
import com.sugamflow.school.subscription.config.SubscriptionEntitlementsProperties;
import com.sugamflow.school.subscription.config.SubscriptionEntitlementsProperties.ReadMode;
import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.persistence.entity.PlanFeatureEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanLimitEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanModuleEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionPlanEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.repo.PlanFeatureRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanLimitRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanModuleRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPlanRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

  private final SubscriptionPlanRepository planRepository;
  private final TenantSubscriptionRepository tenantSubscriptionRepository;
  private final PlanProjectionService planProjectionService;
  private final PlanFeatureRepository planFeatureRepository;
  private final PlanLimitRepository planLimitRepository;
  private final PlanModuleRepository planModuleRepository;
  private final SubscriptionLifecycleService lifecycleService;
  private final SubscriptionCacheSupport cache;
  private final SubscriptionEntitlementsProperties entitlementsProperties;

  public SubscriptionService(
      SubscriptionPlanRepository planRepository,
      TenantSubscriptionRepository tenantSubscriptionRepository,
      PlanProjectionService planProjectionService,
      PlanFeatureRepository planFeatureRepository,
      PlanLimitRepository planLimitRepository,
      PlanModuleRepository planModuleRepository,
      SubscriptionLifecycleService lifecycleService,
      SubscriptionCacheSupport cache,
      SubscriptionEntitlementsProperties entitlementsProperties) {
    this.planRepository = planRepository;
    this.tenantSubscriptionRepository = tenantSubscriptionRepository;
    this.planProjectionService = planProjectionService;
    this.planFeatureRepository = planFeatureRepository;
    this.planLimitRepository = planLimitRepository;
    this.planModuleRepository = planModuleRepository;
    this.lifecycleService = lifecycleService;
    this.cache = cache;
    this.entitlementsProperties = entitlementsProperties;
  }

  @Transactional(readOnly = true)
  public List<SubscriptionPlan> listPlans() {
    return cache.getPlans(
        () -> planRepository.findAll().stream().map(this::toModel).collect(Collectors.toList()));
  }

  @Transactional(readOnly = true)
  public SubscriptionPlan getPlan(String planId) {
    return cache.getPlan(planId, () -> planRepository.findById(planId).map(this::toModel).orElse(null));
  }

  @Transactional
  public SubscriptionPlan savePlan(SubscriptionPlan plan) {
    SubscriptionPlanEntity entity = toEntity(plan);
    entity.setUpdatedAt(Instant.now());
    // Flush parent before plan_feature projection dual-write (FK plan_feature_plan_id_fkey).
    SubscriptionPlanEntity saved = planRepository.saveAndFlush(entity);
    // Dual-write projection; entitlements continue to read JSON only.
    planProjectionService.syncFromPlanEntity(saved);
    // Plan JSON change affects every tenant on this plan — clear plan + entitlement caches.
    cache.evictPlan(saved.getId());
    cache.evictAllEntitlements();
    return toModel(saved);
  }

  @Transactional
  public Map<String, Object> assignPlan(String organizationId, String planId) {
    if (!planRepository.existsById(planId)) {
      throw new IllegalArgumentException("Unknown plan: " + planId);
    }
    TenantSubscriptionEntity entity =
        tenantSubscriptionRepository.findById(organizationId).orElseGet(TenantSubscriptionEntity::new);
    entity.setOrganizationId(organizationId);
    entity.setPlanId(planId);
    entity.setAssignedAt(Instant.now());
    tenantSubscriptionRepository.save(entity);
    // Phase 3: ensure ACTIVE-forever lifecycle with enforcement off (idempotent).
    lifecycleService.ensureActiveForever(organizationId);
    cache.evictEntitlements(organizationId);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("organizationId", organizationId);
    result.put("planId", planId);
    result.put("plan", getPlan(planId));
    result.put("license", lifecycleService.getLicense(organizationId));
    return result;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> entitlements(String organizationId) {
    return cache.getEntitlements(organizationId, () -> loadEntitlements(organizationId));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> flag(String organizationId, String flag) {
    Map<String, Object> entitlements = entitlements(organizationId);
    @SuppressWarnings("unchecked")
    Map<String, Boolean> flags = (Map<String, Boolean>) entitlements.get("featureFlags");
    boolean enabled = flags != null && Boolean.TRUE.equals(flags.get(flag));
    return Map.of(
        "flag", flag, "enabled", enabled, "planId", String.valueOf(entitlements.get("planId")));
  }

  private Map<String, Object> loadEntitlements(String organizationId) {
    String planId =
        tenantSubscriptionRepository
            .findById(organizationId)
            .map(TenantSubscriptionEntity::getPlanId)
            .orElse("starter");
    SubscriptionPlan plan = getPlan(planId);
    if (plan == null) {
      plan = getPlan("starter");
      planId = "starter";
    }
    if (plan == null) {
      throw new IllegalStateException(
          "No subscription plan catalog available (missing plan '"
              + planId
              + "' and fallback 'starter'). Check school_subscription_db.subscription_plan.");
    }

    Map<String, Boolean> jsonFlags =
        plan.getFeatureFlags() != null
            ? new LinkedHashMap<>(plan.getFeatureFlags())
            : new LinkedHashMap<>();
    Map<String, Long> jsonLimits =
        plan.getLimits() != null ? new LinkedHashMap<>(plan.getLimits()) : new LinkedHashMap<>();

    List<PlanFeatureEntity> projectedFeatures =
        planFeatureRepository.findByPlanIdOrderByFeatureCodeAsc(planId);
    List<PlanLimitEntity> projectedLimits =
        planLimitRepository.findByPlanIdOrderByLimitCodeAsc(planId);

    Map<String, Boolean> projectionFlags = new LinkedHashMap<>();
    for (PlanFeatureEntity row : projectedFeatures) {
      projectionFlags.put(row.getFeatureCode(), row.isEnabled());
    }
    Map<String, Long> projectionLimits = new LinkedHashMap<>();
    for (PlanLimitEntity row : projectedLimits) {
      projectionLimits.put(row.getLimitCode(), row.getLimitValue());
    }

    ReadMode mode = entitlementsProperties.resolvedMode();
    Map<String, Boolean> featureFlags;
    Map<String, Long> limits;
    String source;
    switch (mode) {
      case DUAL -> {
        featureFlags = new LinkedHashMap<>(jsonFlags);
        limits = new LinkedHashMap<>(jsonLimits);
        if (entitlementsProperties.isPreferProjection()) {
          featureFlags.putAll(projectionFlags);
          limits.putAll(projectionLimits);
          source = "dual-prefer-projection";
        } else {
          for (Map.Entry<String, Boolean> e : projectionFlags.entrySet()) {
            featureFlags.putIfAbsent(e.getKey(), e.getValue());
          }
          for (Map.Entry<String, Long> e : projectionLimits.entrySet()) {
            limits.putIfAbsent(e.getKey(), e.getValue());
          }
          source = "dual";
        }
      }
      case PROJECTION -> {
        if (!projectionFlags.isEmpty() || !projectionLimits.isEmpty()) {
          featureFlags =
              projectionFlags.isEmpty() ? new LinkedHashMap<>(jsonFlags) : projectionFlags;
          limits = projectionLimits.isEmpty() ? new LinkedHashMap<>(jsonLimits) : projectionLimits;
          source = "projection";
        } else {
          featureFlags = jsonFlags;
          limits = jsonLimits;
          source = "json";
        }
      }
      default -> {
        featureFlags = jsonFlags;
        limits = jsonLimits;
        source = "json";
      }
    }

    List<String> modules = new ArrayList<>();
    for (PlanModuleEntity row : planModuleRepository.findByPlanIdOrderByModuleCodeAsc(planId)) {
      if (row.isEnabled() && row.getModuleCode() != null && !row.getModuleCode().isBlank()) {
        modules.add(row.getModuleCode());
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("organizationId", organizationId);
    result.put("planId", planId);
    result.put("limits", limits);
    result.put("featureFlags", featureFlags);
    result.put("entitlementSource", source);
    result.put("modules", modules);
    return result;
  }

  private SubscriptionPlan toModel(SubscriptionPlanEntity entity) {
    SubscriptionPlan plan = new SubscriptionPlan();
    plan.setId(entity.getId());
    plan.setCode(entity.getCode());
    plan.setName(entity.getName());
    plan.setPlanType(entity.getPlanType());
    plan.setActive(entity.isActive());
    Map<String, Long> limits = new LinkedHashMap<>();
    if (entity.getLimitsJson() != null) {
      entity.getLimitsJson().forEach((k, v) -> limits.put(k, v == null ? null : Long.valueOf(String.valueOf(v))));
    }
    plan.setLimits(limits);
    Map<String, Boolean> flags = new LinkedHashMap<>();
    if (entity.getFeatureFlagsJson() != null) {
      entity
          .getFeatureFlagsJson()
          .forEach((k, v) -> flags.put(k, v != null && Boolean.parseBoolean(String.valueOf(v))));
    }
    plan.setFeatureFlags(flags);
    return plan;
  }

  private SubscriptionPlanEntity toEntity(SubscriptionPlan plan) {
    SubscriptionPlanEntity entity =
        planRepository.findById(plan.getId()).orElseGet(SubscriptionPlanEntity::new);
    entity.setId(plan.getId());
    entity.setCode(plan.getCode());
    entity.setName(plan.getName());
    entity.setPlanType(plan.getPlanType());
    entity.setActive(plan.isActive());
    Map<String, Object> limits = new LinkedHashMap<>();
    if (plan.getLimits() != null) {
      limits.putAll(plan.getLimits());
    }
    entity.setLimitsJson(limits);
    Map<String, Object> flags = new LinkedHashMap<>();
    if (plan.getFeatureFlags() != null) {
      flags.putAll(plan.getFeatureFlags());
    }
    entity.setFeatureFlagsJson(flags);
    return entity;
  }
}
