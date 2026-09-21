package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.persistence.entity.AddonDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.RenewalOpportunityEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionLifecycleEntity;
import com.sugamflow.school.subscription.persistence.entity.UsageCounterEntity;
import com.sugamflow.school.subscription.persistence.repo.AddonDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.RenewalOpportunityRepository;
import com.sugamflow.school.subscription.persistence.repo.RenewalReminderRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPaymentRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionLifecycleRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import com.sugamflow.school.subscription.persistence.repo.UsageCounterRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RenewalCrmServiceTest {

  @Mock private RenewalOpportunityRepository opportunityRepository;
  @Mock private RenewalReminderRepository reminderRepository;
  @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
  @Mock private TenantSubscriptionLifecycleRepository lifecycleRepository;
  @Mock private SubscriptionPaymentRepository paymentRepository;
  @Mock private UsageCounterRepository usageCounterRepository;
  @Mock private AddonDefinitionRepository addonDefinitionRepository;
  @Mock private SubscriptionService subscriptionService;
  @Mock private SubscriptionLifecycleService lifecycleService;

  private RenewalCrmService service;

  @BeforeEach
  void setUp() {
    service =
        new RenewalCrmService(
            opportunityRepository,
            reminderRepository,
            tenantSubscriptionRepository,
            lifecycleRepository,
            paymentRepository,
            usageCounterRepository,
            addonDefinitionRepository,
            subscriptionService,
            lifecycleService);
  }

  @Test
  void healthMarksRenewalWindowAsWatchOrLower() {
    TenantSubscriptionLifecycleEntity life = new TenantSubscriptionLifecycleEntity();
    life.setOrganizationId("ORG1");
    life.setStatus("ACTIVE");
    life.setExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));
    when(lifecycleRepository.findById("ORG1")).thenReturn(Optional.of(life));
    when(paymentRepository.findSuccessfulSince(any())).thenReturn(List.of());
    when(paymentRepository.findByOrganizationIdOrderByCreatedAtDesc("ORG1")).thenReturn(List.of());
    when(usageCounterRepository.findByOrganizationIdOrderByLimitCodeAscPeriodKeyAsc("ORG1"))
        .thenReturn(List.of());
    when(tenantSubscriptionRepository.findById("ORG1")).thenReturn(Optional.of(tenant("ORG1", "starter")));
    when(lifecycleService.ensureActiveForever("ORG1")).thenReturn(life);
    when(opportunityRepository.findByOrganizationId("ORG1")).thenReturn(Optional.empty());
    AtomicLong ids = new AtomicLong(1);
    when(opportunityRepository.save(any()))
        .thenAnswer(
            inv -> {
              RenewalOpportunityEntity o = inv.getArgument(0);
              if (o.getId() == null) {
                o.setId(ids.getAndIncrement());
              }
              return o;
            });

    Map<String, Object> health = service.health("ORG1");
    assertTrue(((Number) health.get("score")).intValue() < 70);
    assertEquals("RENEWAL_DUE", health.get("stage"));
  }

  @Test
  void upsellHintsNearLimitSuggestsAddonSku() {
    SubscriptionPlan plan = new SubscriptionPlan();
    plan.setId("starter");
    plan.setLimits(Map.of("maxUsers", 10L));
    plan.setFeatureFlags(Map.of("FEATURE_FEE", true, "FEATURE_AI", false, "FEATURE_API_ACCESS", false));
    when(tenantSubscriptionRepository.findById("ORG1")).thenReturn(Optional.of(tenant("ORG1", "starter")));
    when(subscriptionService.getPlan("starter")).thenReturn(plan);

    UsageCounterEntity u = new UsageCounterEntity();
    u.setOrganizationId("ORG1");
    u.setLimitCode("maxUsers");
    u.setUsedValue(9);
    when(usageCounterRepository.findByOrganizationIdOrderByLimitCodeAscPeriodKeyAsc("ORG1"))
        .thenReturn(List.of(u));

    AddonDefinitionEntity addon = new AddonDefinitionEntity();
    addon.setSku("ADDON_SEAT");
    addon.setMeterCode("maxUsers");
    addon.setActive(true);
    when(addonDefinitionRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(addon));

    List<Map<String, Object>> hints = service.upsellHints("ORG1");
    assertTrue(hints.stream().anyMatch(h -> "NEAR_LIMIT".equals(h.get("type"))));
    assertEquals("ADDON_SEAT", hints.get(0).get("suggestedSku"));
  }

  @Test
  void listByStageReturnsMatchingOpportunities() {
    RenewalOpportunityEntity opp = new RenewalOpportunityEntity();
    opp.setId(1L);
    opp.setOrganizationId("ORG1");
    opp.setPlanId("starter");
    opp.setStage("ACTIVE");
    opp.setHealthScore(80);
    when(opportunityRepository.findByStageIgnoreCaseOrderByHealthScoreAsc("ACTIVE"))
        .thenReturn(List.of(opp));
    when(lifecycleRepository.findById("ORG1")).thenReturn(Optional.empty());
    when(tenantSubscriptionRepository.findById("ORG1")).thenReturn(Optional.of(tenant("ORG1", "starter")));
    when(subscriptionService.getPlan("starter")).thenReturn(null);
    when(usageCounterRepository.findByOrganizationIdOrderByLimitCodeAscPeriodKeyAsc("ORG1"))
        .thenReturn(List.of());
    when(addonDefinitionRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());

    List<Map<String, Object>> rows = service.pipeline(30, "ACTIVE");
    assertEquals(1, rows.size());
    assertEquals("ORG1", rows.get(0).get("organizationId"));
    assertEquals("ACTIVE", rows.get(0).get("stage"));
  }

  @Test
  void generateRemindersCreatesT30T14T7() {
    TenantSubscriptionEntity t = tenant("ORG1", "starter");
    when(tenantSubscriptionRepository.findAll()).thenReturn(List.of(t));
    when(tenantSubscriptionRepository.findById("ORG1")).thenReturn(Optional.of(t));

    TenantSubscriptionLifecycleEntity life = new TenantSubscriptionLifecycleEntity();
    life.setOrganizationId("ORG1");
    life.setStatus("ACTIVE");
    life.setExpiresAt(Instant.now().plus(40, ChronoUnit.DAYS));
    when(lifecycleRepository.findById("ORG1")).thenReturn(Optional.of(life));
    when(lifecycleService.ensureActiveForever("ORG1")).thenReturn(life);
    when(paymentRepository.findSuccessfulSince(any())).thenReturn(List.of());
    when(paymentRepository.findByOrganizationIdOrderByCreatedAtDesc("ORG1")).thenReturn(List.of());
    when(usageCounterRepository.findByOrganizationIdOrderByLimitCodeAscPeriodKeyAsc("ORG1"))
        .thenReturn(List.of());
    when(opportunityRepository.findByOrganizationId("ORG1")).thenReturn(Optional.empty());
    when(opportunityRepository.save(any()))
        .thenAnswer(
            inv -> {
              RenewalOpportunityEntity o = inv.getArgument(0);
              o.setId(1L);
              return o;
            });
    when(reminderRepository.findByOrganizationIdAndReminderTypeAndDueAtAndStatusIgnoreCase(
            any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(reminderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findAll()).thenReturn(List.of());
    when(reminderRepository.countByStatusIgnoreCase("PENDING")).thenReturn(3L);

    Map<String, Object> out = service.generateReminders();
    assertEquals(3, ((Number) out.get("created")).intValue());
  }

  private static TenantSubscriptionEntity tenant(String org, String plan) {
    TenantSubscriptionEntity t = new TenantSubscriptionEntity();
    t.setOrganizationId(org);
    t.setPlanId(plan);
    return t;
  }
}
