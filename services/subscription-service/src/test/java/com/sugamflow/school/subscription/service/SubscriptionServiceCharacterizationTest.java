package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.cache.SubscriptionCacheSupport;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionPlanEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPlanRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Characterization tests: lock existing entitlements / feature-flag contracts so Phase 1 catalog
 * work cannot silently change School ERP behavior.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceCharacterizationTest {

  private static final String ORG = "SCH-01";

  @Mock private SubscriptionPlanRepository planRepository;
  @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
  @Mock private PlanProjectionService planProjectionService;
  @Mock private SubscriptionLifecycleService lifecycleService;
  @Mock private SubscriptionCacheSupport cache;

  private SubscriptionService service;

  @BeforeEach
  void setUp() {
    service =
        new SubscriptionService(
            planRepository,
            tenantSubscriptionRepository,
            planProjectionService,
            lifecycleService,
            cache);
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
  void entitlementsDefaultsToStarterWhenNoAssignment() {
    when(tenantSubscriptionRepository.findById(ORG)).thenReturn(Optional.empty());
    when(planRepository.findById("starter")).thenReturn(Optional.of(starterEntity()));

    Map<String, Object> out = service.entitlements(ORG);

    assertEquals(ORG, out.get("organizationId"));
    assertEquals("starter", out.get("planId"));
    @SuppressWarnings("unchecked")
    Map<String, Long> limits = (Map<String, Long>) out.get("limits");
    @SuppressWarnings("unchecked")
    Map<String, Boolean> flags = (Map<String, Boolean>) out.get("featureFlags");
    assertEquals(200L, limits.get("maxStudents"));
    assertEquals(3L, limits.get("maxBranches"));
    assertTrue(Boolean.TRUE.equals(flags.get("FEATURE_FEE")));
    assertFalse(Boolean.TRUE.equals(flags.get("FEATURE_WHITE_LABEL")));
  }

  @Test
  void entitlementsUsesAssignedPlan() {
    TenantSubscriptionEntity assignment = new TenantSubscriptionEntity();
    assignment.setOrganizationId(ORG);
    assignment.setPlanId("enterprise");
    when(tenantSubscriptionRepository.findById(ORG)).thenReturn(Optional.of(assignment));
    when(planRepository.findById("enterprise")).thenReturn(Optional.of(enterpriseEntity()));

    Map<String, Object> out = service.entitlements(ORG);

    assertEquals("enterprise", out.get("planId"));
    @SuppressWarnings("unchecked")
    Map<String, Long> limits = (Map<String, Long>) out.get("limits");
    assertEquals(-1L, limits.get("maxBranches"));
    @SuppressWarnings("unchecked")
    Map<String, Boolean> flags = (Map<String, Boolean>) out.get("featureFlags");
    assertTrue(Boolean.TRUE.equals(flags.get("FEATURE_WHITE_LABEL")));
  }

  @Test
  void entitlementsFallsBackToStarterWhenAssignedPlanMissing() {
    TenantSubscriptionEntity assignment = new TenantSubscriptionEntity();
    assignment.setOrganizationId(ORG);
    assignment.setPlanId("ghost");
    when(tenantSubscriptionRepository.findById(ORG)).thenReturn(Optional.of(assignment));
    when(planRepository.findById("ghost")).thenReturn(Optional.empty());
    when(planRepository.findById("starter")).thenReturn(Optional.of(starterEntity()));

    Map<String, Object> out = service.entitlements(ORG);

    assertEquals("starter", out.get("planId"));
    assertEquals(ORG, out.get("organizationId"));
  }

  @Test
  void flagReturnsEnabledFalseWhenMissing() {
    when(tenantSubscriptionRepository.findById(ORG)).thenReturn(Optional.empty());
    when(planRepository.findById("starter")).thenReturn(Optional.of(starterEntity()));

    Map<String, Object> out = service.flag(ORG, "FEATURE_DOES_NOT_EXIST");

    assertEquals("FEATURE_DOES_NOT_EXIST", out.get("flag"));
    assertEquals(false, out.get("enabled"));
    assertEquals("starter", out.get("planId"));
  }

  @Test
  void flagReturnsEnabledTrueWhenPresent() {
    when(tenantSubscriptionRepository.findById(ORG)).thenReturn(Optional.empty());
    when(planRepository.findById("starter")).thenReturn(Optional.of(starterEntity()));

    Map<String, Object> out = service.flag(ORG, "FEATURE_FEE");

    assertEquals("FEATURE_FEE", out.get("flag"));
    assertEquals(true, out.get("enabled"));
    assertEquals("starter", out.get("planId"));
  }

  private static SubscriptionPlanEntity starterEntity() {
    SubscriptionPlanEntity e = new SubscriptionPlanEntity();
    e.setId("starter");
    e.setCode("STARTER");
    e.setName("Starter");
    e.setPlanType("STARTER");
    e.setActive(true);
    Map<String, Object> limits = new LinkedHashMap<>();
    limits.put("maxStudents", 200);
    limits.put("maxBranches", 3);
    e.setLimitsJson(limits);
    Map<String, Object> flags = new LinkedHashMap<>();
    flags.put("FEATURE_FEE", true);
    flags.put("FEATURE_WHITE_LABEL", false);
    e.setFeatureFlagsJson(flags);
    return e;
  }

  private static SubscriptionPlanEntity enterpriseEntity() {
    SubscriptionPlanEntity e = new SubscriptionPlanEntity();
    e.setId("enterprise");
    e.setCode("ENTERPRISE");
    e.setName("Enterprise");
    e.setPlanType("ENTERPRISE");
    e.setActive(true);
    Map<String, Object> limits = new LinkedHashMap<>();
    limits.put("maxBranches", -1);
    e.setLimitsJson(limits);
    Map<String, Object> flags = new LinkedHashMap<>();
    flags.put("FEATURE_WHITE_LABEL", true);
    e.setFeatureFlagsJson(flags);
    return e;
  }
}
