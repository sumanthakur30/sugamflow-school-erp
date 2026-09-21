package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.persistence.entity.LimitDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.UsageCounterEntity;
import com.sugamflow.school.subscription.persistence.entity.UsageEventEntity;
import com.sugamflow.school.subscription.persistence.repo.LimitDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.UsageCounterRepository;
import com.sugamflow.school.subscription.persistence.repo.UsageEventRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageMeteringServiceTest {

  private static final String ORG = "SCH-01";

  @Mock private UsageCounterRepository usageCounterRepository;
  @Mock private UsageEventRepository usageEventRepository;
  @Mock private LimitDefinitionRepository limitDefinitionRepository;
  @Mock private SubscriptionService subscriptionService;

  private UsageMeteringService service;

  @BeforeEach
  void setUp() {
    service =
        new UsageMeteringService(
            usageCounterRepository,
            usageEventRepository,
            limitDefinitionRepository,
            subscriptionService);
  }

  @Test
  void validateLimitAllowsWhenUnderCap() {
    when(subscriptionService.entitlements(ORG)).thenReturn(entitlements(3L));
    when(limitDefinitionRepository.findById("maxBranches")).thenReturn(Optional.empty());
    when(usageCounterRepository.findByOrganizationIdAndLimitCodeAndPeriodKey(
            ORG, "maxBranches", "ALL"))
        .thenReturn(Optional.of(counter(2)));

    Map<String, Object> out =
        service.validateLimit(ORG, Map.of("limitCode", "maxBranches", "delta", 1));

    assertEquals(true, out.get("allowed"));
    assertEquals(2L, out.get("used"));
    assertEquals(3L, out.get("limit"));
    assertEquals(1L, out.get("remaining"));
  }

  @Test
  void validateLimitDeniesWhenWouldExceed() {
    when(subscriptionService.entitlements(ORG)).thenReturn(entitlements(3L));
    when(limitDefinitionRepository.findById("maxBranches")).thenReturn(Optional.empty());
    when(usageCounterRepository.findByOrganizationIdAndLimitCodeAndPeriodKey(
            ORG, "maxBranches", "ALL"))
        .thenReturn(Optional.of(counter(3)));

    Map<String, Object> out =
        service.validateLimit(ORG, Map.of("limitCode", "maxBranches", "delta", 1));

    assertEquals(false, out.get("allowed"));
    assertEquals(0L, out.get("remaining"));
  }

  @Test
  void validateLimitTreatsMinusOneAsUnlimited() {
    when(subscriptionService.entitlements(ORG)).thenReturn(entitlements(-1L));
    when(limitDefinitionRepository.findById("maxBranches")).thenReturn(Optional.empty());
    when(usageCounterRepository.findByOrganizationIdAndLimitCodeAndPeriodKey(
            ORG, "maxBranches", "ALL"))
        .thenReturn(Optional.of(counter(999)));

    Map<String, Object> out =
        service.validateLimit(ORG, Map.of("limitCode", "maxBranches", "delta", 1));

    assertEquals(true, out.get("allowed"));
    assertEquals(true, out.get("unlimited"));
  }

  @Test
  void validateFeatureUsesFlagContract() {
    when(subscriptionService.flag(ORG, "FEATURE_FEE"))
        .thenReturn(Map.of("flag", "FEATURE_FEE", "enabled", true, "planId", "starter"));

    Map<String, Object> out =
        service.validateFeature(ORG, Map.of("featureCode", "FEATURE_FEE"));

    assertEquals(true, out.get("allowed"));
    assertEquals("starter", out.get("planId"));
  }

  @Test
  void incrementUsagePersistsCounterAndEvent() {
    when(subscriptionService.entitlements(ORG)).thenReturn(entitlements(10L));
    when(limitDefinitionRepository.findById("maxBranches")).thenReturn(Optional.empty());
    when(usageCounterRepository.findByOrganizationIdAndLimitCodeAndPeriodKey(
            ORG, "maxBranches", "ALL"))
        .thenReturn(Optional.empty());
    when(usageCounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(usageEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> out =
        service.incrementUsage(
            ORG, Map.of("limitCode", "maxBranches", "delta", 2, "reason", "branch create"), "user-1");

    assertEquals(true, out.get("applied"));
    assertEquals(2L, out.get("used"));
    ArgumentCaptor<UsageCounterEntity> counterCap = ArgumentCaptor.forClass(UsageCounterEntity.class);
    verify(usageCounterRepository).save(counterCap.capture());
    assertEquals(2L, counterCap.getValue().getUsedValue());
    ArgumentCaptor<UsageEventEntity> eventCap = ArgumentCaptor.forClass(UsageEventEntity.class);
    verify(usageEventRepository).save(eventCap.capture());
    assertEquals(2L, eventCap.getValue().getDelta());
    assertEquals("user-1", eventCap.getValue().getActor());
  }

  @Test
  void incrementWithEnforceRefusesOverage() {
    when(subscriptionService.entitlements(ORG)).thenReturn(entitlements(3L));
    when(limitDefinitionRepository.findById("maxBranches")).thenReturn(Optional.empty());
    when(usageCounterRepository.findByOrganizationIdAndLimitCodeAndPeriodKey(
            ORG, "maxBranches", "ALL"))
        .thenReturn(Optional.of(counter(3)));

    Map<String, Object> out =
        service.incrementUsage(
            ORG, Map.of("limitCode", "maxBranches", "delta", 1, "enforce", true), "user-1");

    assertEquals(false, out.get("applied"));
    assertEquals(false, out.get("allowed"));
  }

  @Test
  void decrementDoesNotGoBelowZero() {
    when(subscriptionService.entitlements(ORG)).thenReturn(entitlements(10L));
    when(limitDefinitionRepository.findById("maxBranches")).thenReturn(Optional.empty());
    when(usageCounterRepository.findByOrganizationIdAndLimitCodeAndPeriodKey(
            ORG, "maxBranches", "ALL"))
        .thenReturn(Optional.of(counter(1)));
    when(usageCounterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(usageEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> out =
        service.decrementUsage(ORG, Map.of("limitCode", "maxBranches", "delta", 5), "user-1");

    assertEquals(true, out.get("applied"));
    assertEquals(0L, out.get("used"));
  }

  @Test
  void monthlyAggregationUsesYearMonthPeriod() {
    LimitDefinitionEntity def = new LimitDefinitionEntity();
    def.setCode("maxSms");
    def.setAggregation("MONTHLY");
    when(limitDefinitionRepository.findById("maxSms")).thenReturn(Optional.of(def));
    when(subscriptionService.entitlements(ORG))
        .thenReturn(
            Map.of(
                "organizationId",
                ORG,
                "planId",
                "starter",
                "limits",
                Map.of("maxSms", 500L),
                "featureFlags",
                Map.of()));
    when(usageCounterRepository.findByOrganizationIdAndLimitCodeAndPeriodKey(
            any(), any(), any()))
        .thenReturn(Optional.empty());

    Map<String, Object> out = service.validateLimit(ORG, Map.of("limitCode", "maxSms", "delta", 1));

    assertTrue(String.valueOf(out.get("periodKey")).matches("\\d{4}-\\d{2}"));
    assertFalse("ALL".equals(out.get("periodKey")));
  }

  private static Map<String, Object> entitlements(long maxBranches) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", ORG);
    out.put("planId", "starter");
    out.put("limits", Map.of("maxBranches", maxBranches));
    out.put("featureFlags", Map.of());
    return out;
  }

  private static UsageCounterEntity counter(long used) {
    UsageCounterEntity c = new UsageCounterEntity();
    c.setOrganizationId(ORG);
    c.setLimitCode("maxBranches");
    c.setPeriodKey("ALL");
    c.setUsedValue(used);
    return c;
  }
}
