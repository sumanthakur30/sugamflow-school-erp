package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.cache.SubscriptionCacheSupport;
import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionPlanEntity;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPlanRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SubscriptionPlanSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionPlanSeeder.class);

  private final SubscriptionPlanRepository planRepository;
  private final SubscriptionService subscriptionService;
  private final PlanProjectionService planProjectionService;
  private final SubscriptionCacheSupport cache;

  public SubscriptionPlanSeeder(
      SubscriptionPlanRepository planRepository,
      SubscriptionService subscriptionService,
      PlanProjectionService planProjectionService,
      SubscriptionCacheSupport cache) {
    this.planRepository = planRepository;
    this.subscriptionService = subscriptionService;
    this.planProjectionService = planProjectionService;
    this.cache = cache;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (planRepository.count() == 0) {
      log.info("Seeding default subscription plans");
      seed(SubscriptionPlan.starter());
      seed(named("basic", "Basic", "BASIC", 500));
      seed(named("standard", "Standard", "STANDARD", 1500));
      seed(named("professional", "Professional", "PROFESSIONAL", 5000));
      seed(SubscriptionPlan.enterprise());
      seed(named("government", "Government", "GOVERNMENT", 10000));
      seed(named("trust", "Trust", "TRUST", 8000));
      seed(named("custom", "Custom", "CUSTOM", 0));
    }
    ensureAdmissionFlagOnAllPlans();
    ensureFlagOnAllPlans("FEATURE_FEE");
    ensureFlagOnAllPlans("FEATURE_STUDENT_MASTER");
    ensureFlagOnAllPlans("FEATURE_IMPORT_WORKBENCH");
    ensureFlagOnAllPlans("FEATURE_COMMS_HUB");
    ensureFlagOnAllPlans("FEATURE_LMS");
    ensureFlagOnAllPlans("FEATURE_ATTENDANCE");
    ensureFlagOnAllPlans("FEATURE_EXAM");
    ensureFlagOnAllPlans("FEATURE_LIBRARY");
    ensureFlagOnAllPlans("FEATURE_HOSTEL");
    ensureFlagOnAllPlans("FEATURE_TRANSPORT");
    ensureFlagOnAllPlans("FEATURE_PAYROLL");
    ensureFlagOnAllPlans("FEATURE_STAFF_MASTER");
    ensureFlagOnAllPlans("FEATURE_HR");
    ensureFlagOnAllPlans("FEATURE_AUDIT_LOGS");
    ensureFlagOnAllPlans("FEATURE_REPORT_BUILDER");
    ensureFlagOnAllPlans("FEATURE_PARENT_APP");
    ensureFlagOnAllPlans("FEATURE_TEACHER_APP");
    ensureFlagOnAllPlans("FEATURE_MULTI_BRANCH");
    ensureFlagOnAllPlans("FEATURE_AI");
    ensureFlagOnAllPlans("FEATURE_BIOMETRIC");
    ensureFlagOnAllPlans("FEATURE_FACE_RECOGNITION");
    ensureFlagOnAllPlans("FEATURE_GPS");
    ensureFlagOnAllPlans("FEATURE_OFFLINE_MODE");
    ensureFlagOnAllPlans("FEATURE_ADMIN_CONFIG");
    ensureFlagOnAllPlans("FEATURE_FORM_BUILDER");
    ensureFlagOnAllPlans("FEATURE_WORKFLOW_BUILDER");
    ensureFlagOnAllPlans("FEATURE_RULE_ENGINE");
    ensureFlagOnAllPlans("FEATURE_WHITE_LABEL");
    ensureFlagOnAllPlans("FEATURE_MULTI_PAYMENT_GATEWAY");
    ensureFlagOnAllPlans("FEATURE_ACADEMIC_LIFECYCLE");
    ensureFlagOnAllPlans("FEATURE_OPS_DEPTH");
    ensureLimitAtLeast("maxBranches", 3L);
    // Phase 0 CRM: standalone sellable plans only — never merge School flags onto them.
    ensureCrmStandalonePlans();
    // Phase 2.3: shop vertical plans (hospital/poly/pharmacy/pathlab/retail).
    ensureShopVerticalPlans();
    // Phase 2: project current plan JSON into plan_feature / plan_limit / plan_module.
    planProjectionService.syncAllPlans();
    // Phase 7: seed/flag merges may bypass savePlan — clear Redis snapshots.
    cache.evictAll();
  }

  /** Idempotent CRM SKUs (Flyway V17 is source of truth; seeder covers empty/dev DBs). */
  private void ensureCrmStandalonePlans() {
    seedCrmIfAbsent(SubscriptionPlan.crmStarter());
    seedCrmIfAbsent(SubscriptionPlan.crmProfessional());
    seedCrmIfAbsent(SubscriptionPlan.crmEnterprise());
  }

  private void ensureShopVerticalPlans() {
    seedVerticalIfAbsent(SubscriptionPlan.hospitalStarter());
    seedVerticalIfAbsent(SubscriptionPlan.hospitalPro());
    seedVerticalIfAbsent(SubscriptionPlan.polyStarter());
    seedVerticalIfAbsent(SubscriptionPlan.pharmacyStarter());
    seedVerticalIfAbsent(SubscriptionPlan.pathlabStarter());
    seedVerticalIfAbsent(SubscriptionPlan.retailStarter());
  }

  private void seedCrmIfAbsent(SubscriptionPlan plan) {
    if (planRepository.findById(plan.getId()).isEmpty()) {
      log.info("Seeding CRM plan {}", plan.getId());
      seed(plan);
    }
  }

  private void seedVerticalIfAbsent(SubscriptionPlan plan) {
    if (planRepository.findById(plan.getId()).isEmpty()) {
      log.info("Seeding shop vertical plan {}", plan.getId());
      seed(plan);
    }
  }

  private static boolean isCrmPlan(SubscriptionPlanEntity entity) {
    if (entity == null) {
      return false;
    }
    String id = entity.getId() == null ? "" : entity.getId();
    String type = entity.getPlanType() == null ? "" : entity.getPlanType();
    return id.startsWith("crm-") || type.regionMatches(true, 0, "CRM_", 0, 4);
  }

  /** Shop/CRM vertical SKUs must not receive School FEATURE_* merges. */
  private static boolean isNonSchoolPlan(SubscriptionPlanEntity entity) {
    if (entity == null) {
      return true;
    }
    if (isCrmPlan(entity)) {
      return true;
    }
    String id = entity.getId() == null ? "" : entity.getId();
    String type = entity.getPlanType() == null ? "" : entity.getPlanType().toUpperCase();
    return id.startsWith("hospital-")
        || id.startsWith("poly-")
        || id.startsWith("pharmacy-")
        || id.startsWith("pathlab-")
        || id.startsWith("retail-")
        || id.startsWith("medshop-")
        || type.startsWith("HOSPITAL_")
        || type.startsWith("POLY_")
        || type.startsWith("PHARMACY_")
        || type.startsWith("PATHLAB_")
        || type.startsWith("RETAIL_");
  }

  private void ensureLimitAtLeast(String limitKey, long minValue) {
    int updated = 0;
    for (SubscriptionPlanEntity entity : planRepository.findAll()) {
      if (isNonSchoolPlan(entity)) {
        continue;
      }
      Map<String, Object> limits = entity.getLimitsJson();
      if (limits == null) {
        continue;
      }
      long current = -1L;
      Object raw = limits.get(limitKey);
      if (raw != null) {
        try {
          current = Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
          current = 0L;
        }
      } else {
        current = 0L;
      }
      // -1 means unlimited — leave alone
      if (current >= 0 && current < minValue) {
        limits.put(limitKey, minValue);
        entity.setLimitsJson(limits);
        planRepository.save(entity);
        updated++;
      }
    }
    if (updated > 0) {
      log.info("Raised {} to at least {} on {} subscription plan(s)", limitKey, minValue, updated);
    }
  }

  private void seed(SubscriptionPlan plan) {
    subscriptionService.savePlan(plan);
  }

  /** Phase 5 — merge FEATURE_ADMISSION into existing plan JSON without wipe. */
  private void ensureAdmissionFlagOnAllPlans() {
    ensureFlagOnAllPlans("FEATURE_ADMISSION");
  }

  private void ensureFlagOnAllPlans(String flag) {
    int updated = 0;
    for (SubscriptionPlanEntity entity : planRepository.findAll()) {
      if (isNonSchoolPlan(entity)) {
        continue;
      }
      Map<String, Object> flags = entity.getFeatureFlagsJson();
      if (flags == null) {
        continue;
      }
      boolean enable =
          Boolean.TRUE.equals(flags.get("FEATURE_ADMIN_CONFIG"))
              || "ENTERPRISE".equalsIgnoreCase(entity.getPlanType())
              || "PROFESSIONAL".equalsIgnoreCase(entity.getPlanType())
              || "STARTER".equalsIgnoreCase(entity.getPlanType())
              || "BASIC".equalsIgnoreCase(entity.getPlanType())
              || "STANDARD".equalsIgnoreCase(entity.getPlanType());
      boolean needsUpdate = false;
      if (!flags.containsKey(flag)) {
        flags.put(flag, enable);
        needsUpdate = true;
      } else if (enable && Boolean.FALSE.equals(flags.get(flag))) {
        flags.put(flag, true);
        needsUpdate = true;
      }
      if (needsUpdate) {
        entity.setFeatureFlagsJson(flags);
        planRepository.save(entity);
        updated++;
      }
    }
    if (updated > 0) {
      log.info("Enabled {} on {} subscription plan(s)", flag, updated);
    }
  }

  private SubscriptionPlan named(String id, String name, String type, long students) {
    SubscriptionPlan p = SubscriptionPlan.starter();
    p.setId(id);
    p.setCode(id.toUpperCase());
    p.setName(name);
    p.setPlanType(type);
    if (students > 0) {
      p.getLimits().put("maxStudents", students);
    }
    if ("PROFESSIONAL".equals(type) || "GOVERNMENT".equals(type) || "TRUST".equals(type)) {
      p.getFeatureFlags().replaceAll((k, v) -> true);
    }
    return p;
  }
}
