package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.persistence.entity.FeatureDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanFeatureEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanLimitEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanModuleEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionPlanEntity;
import com.sugamflow.school.subscription.persistence.repo.FeatureDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanFeatureRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanLimitRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanModuleRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPlanRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dual-write projection of plan JSON into normalized plan_* tables.
 * Phase 2: entitlements may dual-read these tables when
 * {@code subscription.entitlements.read-mode} is {@code dual} or {@code projection}.
 */
@Service
public class PlanProjectionService {

  private static final Logger log = LoggerFactory.getLogger(PlanProjectionService.class);

  private final PlanFeatureRepository planFeatureRepository;
  private final PlanLimitRepository planLimitRepository;
  private final PlanModuleRepository planModuleRepository;
  private final FeatureDefinitionRepository featureDefinitionRepository;
  private final SubscriptionPlanRepository planRepository;

  public PlanProjectionService(
      PlanFeatureRepository planFeatureRepository,
      PlanLimitRepository planLimitRepository,
      PlanModuleRepository planModuleRepository,
      FeatureDefinitionRepository featureDefinitionRepository,
      SubscriptionPlanRepository planRepository) {
    this.planFeatureRepository = planFeatureRepository;
    this.planLimitRepository = planLimitRepository;
    this.planModuleRepository = planModuleRepository;
    this.featureDefinitionRepository = featureDefinitionRepository;
    this.planRepository = planRepository;
  }

  @Transactional
  public void syncFromPlanEntity(SubscriptionPlanEntity plan) {
    if (plan == null || plan.getId() == null) {
      return;
    }
    replaceProjection(plan.getId(), plan.getFeatureFlagsJson(), plan.getLimitsJson());
  }

  @Transactional
  public void replaceProjection(
      String planId, Map<String, Object> featureFlagsJson, Map<String, Object> limitsJson) {
    Instant now = Instant.now();

    planFeatureRepository.deleteByPlanId(planId);
    planLimitRepository.deleteByPlanId(planId);
    planModuleRepository.deleteByPlanId(planId);

    Map<String, Boolean> flags = toBooleanMap(featureFlagsJson);
    List<PlanFeatureEntity> features = new ArrayList<>();
    for (Map.Entry<String, Boolean> e : flags.entrySet()) {
      PlanFeatureEntity row = new PlanFeatureEntity();
      row.setPlanId(planId);
      row.setFeatureCode(e.getKey());
      row.setEnabled(Boolean.TRUE.equals(e.getValue()));
      row.setUpdatedAt(now);
      features.add(row);
    }
    if (!features.isEmpty()) {
      planFeatureRepository.saveAll(features);
    }

    Map<String, Long> limits = toLongMap(limitsJson);
    List<PlanLimitEntity> limitRows = new ArrayList<>();
    for (Map.Entry<String, Long> e : limits.entrySet()) {
      if (e.getValue() == null) {
        continue;
      }
      PlanLimitEntity row = new PlanLimitEntity();
      row.setPlanId(planId);
      row.setLimitCode(e.getKey());
      row.setLimitValue(e.getValue());
      row.setUpdatedAt(now);
      limitRows.add(row);
    }
    if (!limitRows.isEmpty()) {
      planLimitRepository.saveAll(limitRows);
    }

    Map<String, Boolean> moduleEnabled = deriveModules(flags);
    List<PlanModuleEntity> modules = new ArrayList<>();
    for (Map.Entry<String, Boolean> e : moduleEnabled.entrySet()) {
      PlanModuleEntity row = new PlanModuleEntity();
      row.setPlanId(planId);
      row.setModuleCode(e.getKey());
      row.setEnabled(Boolean.TRUE.equals(e.getValue()));
      row.setUpdatedAt(now);
      modules.add(row);
    }
    if (!modules.isEmpty()) {
      planModuleRepository.saveAll(modules);
    }
  }

  /** Backfill projection for every plan from current JSON (idempotent replace). */
  @Transactional
  public int syncAllPlans() {
    int count = 0;
    for (SubscriptionPlanEntity plan : planRepository.findAll()) {
      syncFromPlanEntity(plan);
      count++;
    }
    if (count > 0) {
      log.info("Projected plan composition for {} subscription plan(s)", count);
    }
    return count;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getProjection(String planId) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("planId", planId);
    out.put("sourceOfTruth", "subscription_plan JSON");
    out.put("features", listFeatures(planId));
    out.put("limits", listLimits(planId));
    out.put("modules", listModules(planId));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listFeatures(String planId) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (PlanFeatureEntity e : planFeatureRepository.findByPlanIdOrderByFeatureCodeAsc(planId)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("featureCode", e.getFeatureCode());
      m.put("enabled", e.isEnabled());
      rows.add(m);
    }
    return rows;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listLimits(String planId) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (PlanLimitEntity e : planLimitRepository.findByPlanIdOrderByLimitCodeAsc(planId)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("limitCode", e.getLimitCode());
      m.put("limitValue", e.getLimitValue());
      rows.add(m);
    }
    return rows;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listModules(String planId) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (PlanModuleEntity e : planModuleRepository.findByPlanIdOrderByModuleCodeAsc(planId)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("moduleCode", e.getModuleCode());
      m.put("enabled", e.isEnabled());
      rows.add(m);
    }
    return rows;
  }

  /**
   * A module is enabled when at least one of its catalog features is enabled on the plan. Unknown
   * feature codes (not in catalog) do not create modules.
   */
  private Map<String, Boolean> deriveModules(Map<String, Boolean> flags) {
    if (flags.isEmpty()) {
      return Map.of();
    }
    Map<String, String> featureToModule = new HashMap<>();
    for (FeatureDefinitionEntity def : featureDefinitionRepository.findAll()) {
      featureToModule.put(def.getCode(), def.getModuleCode());
    }
    Set<String> touchedModules = new HashSet<>();
    Map<String, Boolean> moduleEnabled = new LinkedHashMap<>();
    for (Map.Entry<String, Boolean> e : flags.entrySet()) {
      String moduleCode = featureToModule.get(e.getKey());
      if (moduleCode == null) {
        continue;
      }
      touchedModules.add(moduleCode);
      if (Boolean.TRUE.equals(e.getValue())) {
        moduleEnabled.put(moduleCode, true);
      } else {
        moduleEnabled.putIfAbsent(moduleCode, false);
      }
    }
    for (String moduleCode : touchedModules) {
      moduleEnabled.putIfAbsent(moduleCode, false);
    }
    return moduleEnabled;
  }

  private static Map<String, Boolean> toBooleanMap(Map<String, Object> raw) {
    Map<String, Boolean> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    raw.forEach((k, v) -> out.put(k, v != null && Boolean.parseBoolean(String.valueOf(v))));
    return out;
  }

  private static Map<String, Long> toLongMap(Map<String, Object> raw) {
    Map<String, Long> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    raw.forEach(
        (k, v) -> {
          if (v == null) {
            return;
          }
          try {
            out.put(k, Long.valueOf(String.valueOf(v)));
          } catch (NumberFormatException ignored) {
            // skip non-numeric limit values
          }
        });
    return out;
  }
}
