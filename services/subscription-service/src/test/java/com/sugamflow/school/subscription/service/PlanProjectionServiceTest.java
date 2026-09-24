package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.cache.SubscriptionCacheSupport;
import com.sugamflow.school.subscription.config.SubscriptionEntitlementsProperties;
import com.sugamflow.school.subscription.model.SubscriptionPlan;
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
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanProjectionServiceTest {

  @Mock private PlanFeatureRepository planFeatureRepository;
  @Mock private PlanLimitRepository planLimitRepository;
  @Mock private PlanModuleRepository planModuleRepository;
  @Mock private FeatureDefinitionRepository featureDefinitionRepository;
  @Mock private SubscriptionPlanRepository planRepository;
  @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
  @Mock private SubscriptionLifecycleService lifecycleService;
  @Mock private SubscriptionCacheSupport cache;

  private PlanProjectionService projectionService;
  private SubscriptionService subscriptionService;

  @BeforeEach
  void setUp() {
    projectionService =
        new PlanProjectionService(
            planFeatureRepository,
            planLimitRepository,
            planModuleRepository,
            featureDefinitionRepository,
            planRepository);
    SubscriptionEntitlementsProperties entitlementsProperties =
        new SubscriptionEntitlementsProperties();
    entitlementsProperties.setReadMode("json");
    subscriptionService =
        new SubscriptionService(
            planRepository,
            tenantSubscriptionRepository,
            projectionService,
            planFeatureRepository,
            planLimitRepository,
            planModuleRepository,
            lifecycleService,
            cache,
            entitlementsProperties);
    lenient()
        .when(cache.getEntitlements(any(), any()))
        .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());
    lenient()
        .when(cache.getPlan(any(), any()))
        .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());
    lenient()
        .when(cache.getPlans(any()))
        .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
  }

  @Test
  void replaceProjectionWritesFeaturesLimitsAndDerivedModules() {
    FeatureDefinitionEntity fee = new FeatureDefinitionEntity();
    fee.setCode("FEATURE_FEE");
    fee.setModuleCode("SCHOOL_FEE");
    FeatureDefinitionEntity hostel = new FeatureDefinitionEntity();
    hostel.setCode("FEATURE_HOSTEL");
    hostel.setModuleCode("SCHOOL_HOSTEL");
    when(featureDefinitionRepository.findAll()).thenReturn(List.of(fee, hostel));

    Map<String, Object> flags = new LinkedHashMap<>();
    flags.put("FEATURE_FEE", true);
    flags.put("FEATURE_HOSTEL", false);
    flags.put("UNKNOWN_FLAG", true);
    Map<String, Object> limits = new LinkedHashMap<>();
    limits.put("maxBranches", 3);
    limits.put("maxStudents", -1);

    projectionService.replaceProjection("starter", flags, limits);

    verify(planFeatureRepository).deleteByPlanId("starter");
    verify(planLimitRepository).deleteByPlanId("starter");
    verify(planModuleRepository).deleteByPlanId("starter");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PlanFeatureEntity>> featureCap = ArgumentCaptor.forClass(List.class);
    verify(planFeatureRepository).saveAll(featureCap.capture());
    assertEquals(3, featureCap.getValue().size());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PlanLimitEntity>> limitCap = ArgumentCaptor.forClass(List.class);
    verify(planLimitRepository).saveAll(limitCap.capture());
    assertEquals(2, limitCap.getValue().size());
    assertEquals(-1L, limitCap.getValue().stream()
        .filter(r -> "maxStudents".equals(r.getLimitCode()))
        .findFirst()
        .orElseThrow()
        .getLimitValue());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PlanModuleEntity>> moduleCap = ArgumentCaptor.forClass(List.class);
    verify(planModuleRepository).saveAll(moduleCap.capture());
    Map<String, Boolean> modules = new LinkedHashMap<>();
    moduleCap.getValue().forEach(m -> modules.put(m.getModuleCode(), m.isEnabled()));
    assertTrue(modules.get("SCHOOL_FEE"));
    assertFalse(modules.get("SCHOOL_HOSTEL"));
    assertFalse(modules.containsKey(null));
  }

  @Test
  void savePlanDualWritesProjectionButEntitlementsStayJson() {
    SubscriptionPlan plan = new SubscriptionPlan();
    plan.setId("starter");
    plan.setCode("STARTER");
    plan.setName("Starter");
    plan.setPlanType("STARTER");
    plan.setLimits(Map.of("maxBranches", 3L));
    plan.setFeatureFlags(Map.of("FEATURE_FEE", true));

    when(planRepository.findById("starter")).thenReturn(Optional.empty());
    when(planRepository.save(any(SubscriptionPlanEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(featureDefinitionRepository.findAll()).thenReturn(List.of());

    subscriptionService.savePlan(plan);

    verify(planFeatureRepository).deleteByPlanId("starter");
    verify(planFeatureRepository).saveAll(anyList());
    verify(planLimitRepository).saveAll(anyList());

    when(tenantSubscriptionRepository.findById("ORG-1")).thenReturn(Optional.empty());
    when(planRepository.findById("starter"))
        .thenReturn(
            Optional.of(
                entity(
                    "starter",
                    Map.of("maxBranches", 3),
                    Map.of("FEATURE_FEE", true))));

    Map<String, Object> entitlements = subscriptionService.entitlements("ORG-1");
    assertEquals("starter", entitlements.get("planId"));
    @SuppressWarnings("unchecked")
    Map<String, Long> limits = (Map<String, Long>) entitlements.get("limits");
    assertEquals(3L, limits.get("maxBranches"));
    // Projection tables are never consulted for entitlements.
    verify(planFeatureRepository, never()).findByPlanIdOrderByFeatureCodeAsc(eq("starter"));
  }

  private static SubscriptionPlanEntity entity(
      String id, Map<String, Object> limits, Map<String, Object> flags) {
    SubscriptionPlanEntity e = new SubscriptionPlanEntity();
    e.setId(id);
    e.setCode(id.toUpperCase());
    e.setName(id);
    e.setPlanType(id.toUpperCase());
    e.setLimitsJson(new LinkedHashMap<>(limits));
    e.setFeatureFlagsJson(new LinkedHashMap<>(flags));
    return e;
  }
}
