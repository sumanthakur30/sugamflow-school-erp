package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.cache.SubscriptionCacheSupport;
import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionPlanEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPlanRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.time.Instant;
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
  private final SubscriptionLifecycleService lifecycleService;
  private final SubscriptionCacheSupport cache;

  public SubscriptionService(
      SubscriptionPlanRepository planRepository,
      TenantSubscriptionRepository tenantSubscriptionRepository,
      PlanProjectionService planProjectionService,
      SubscriptionLifecycleService lifecycleService,
      SubscriptionCacheSupport cache) {
    this.planRepository = planRepository;
    this.tenantSubscriptionRepository = tenantSubscriptionRepository;
    this.planProjectionService = planProjectionService;
    this.lifecycleService = lifecycleService;
    this.cache = cache;
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
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("organizationId", organizationId);
    result.put("planId", planId);
    result.put("limits", plan.getLimits());
    result.put("featureFlags", plan.getFeatureFlags());
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
