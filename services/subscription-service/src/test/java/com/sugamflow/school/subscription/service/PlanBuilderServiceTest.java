package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.persistence.entity.BillingCycleEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanPriceEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanPriceScheduleEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanVersionEntity;
import com.sugamflow.school.subscription.persistence.entity.PriceBookEntity;
import com.sugamflow.school.subscription.persistence.repo.BillingCycleRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanPriceRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanPriceScheduleRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanVersionRepository;
import com.sugamflow.school.subscription.persistence.repo.PriceBookRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanBuilderServiceTest {

  @Mock private PlanVersionRepository versionRepository;
  @Mock private PlanPriceScheduleRepository scheduleRepository;
  @Mock private SubscriptionPlanRepository planRepository;
  @Mock private SubscriptionService subscriptionService;
  @Mock private PlanPriceRepository planPriceRepository;
  @Mock private PriceBookRepository priceBookRepository;
  @Mock private BillingCycleRepository billingCycleRepository;

  private PlanBuilderService service;

  @BeforeEach
  void setUp() {
    service =
        new PlanBuilderService(
            versionRepository,
            scheduleRepository,
            planRepository,
            subscriptionService,
            planPriceRepository,
            priceBookRepository,
            billingCycleRepository);
  }

  @Test
  void ensureDraftCreatesFromLivePlan() {
    when(planRepository.existsById("starter")).thenReturn(true);
    when(versionRepository.findFirstByPlanIdAndStatusIgnoreCaseOrderByVersionNumberDesc(
            "starter", "DRAFT"))
        .thenReturn(Optional.empty());
    when(versionRepository.maxVersionNumber("starter")).thenReturn(0);
    SubscriptionPlan live = new SubscriptionPlan();
    live.setId("starter");
    live.setFeatureFlags(Map.of("FEATURE_FEE", true));
    live.setLimits(Map.of("maxUsers", 30L));
    when(subscriptionService.getPlan("starter")).thenReturn(live);
    AtomicLong ids = new AtomicLong(1);
    when(versionRepository.save(any()))
        .thenAnswer(
            inv -> {
              PlanVersionEntity v = inv.getArgument(0);
              v.setId(ids.getAndIncrement());
              return v;
            });

    Map<String, Object> draft = service.ensureDraft("starter");
    assertEquals("DRAFT", draft.get("status"));
    assertEquals(1, draft.get("versionNumber"));
    assertEquals(true, ((Map<?, ?>) draft.get("featureFlags")).get("FEATURE_FEE"));
  }

  @Test
  void publishAppliesDraftToLivePlan() {
    when(planRepository.existsById("starter")).thenReturn(true);
    PlanVersionEntity draft = new PlanVersionEntity();
    draft.setId(5L);
    draft.setPlanId("starter");
    draft.setVersionNumber(2);
    draft.setStatus("DRAFT");
    draft.setFeatureFlagsJson(Map.of("FEATURE_FEE", true, "FEATURE_AI", true));
    draft.setLimitsJson(Map.of("maxUsers", 40));
    when(versionRepository.findFirstByPlanIdAndStatusIgnoreCaseOrderByVersionNumberDesc(
            "starter", "DRAFT"))
        .thenReturn(Optional.of(draft));
    when(versionRepository.findByPlanIdOrderByVersionNumberDesc("starter"))
        .thenReturn(List.of(draft));
    when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(scheduleRepository.findByStatusIgnoreCaseAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
            any(), any()))
        .thenReturn(List.of());

    SubscriptionPlan live = new SubscriptionPlan();
    live.setId("starter");
    live.setFeatureFlags(Map.of("FEATURE_FEE", false));
    live.setLimits(Map.of("maxUsers", 30L));
    when(subscriptionService.getPlan("starter")).thenReturn(live);
    when(subscriptionService.savePlan(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> out = service.publish("starter", Map.of());
    assertEquals("PUBLISHED", out.get("status"));
    ArgumentCaptor<SubscriptionPlan> cap = ArgumentCaptor.forClass(SubscriptionPlan.class);
    verify(subscriptionService).savePlan(cap.capture());
    assertEquals(true, cap.getValue().getFeatureFlags().get("FEATURE_AI"));
    assertEquals(40L, cap.getValue().getLimits().get("maxUsers"));
  }

  @Test
  void schedulePriceAppliesImmediatelyWhenEffectiveNow() {
    when(planRepository.existsById("starter")).thenReturn(true);
    PriceBookEntity book = new PriceBookEntity();
    book.setId("pb-in-smb");
    when(priceBookRepository.findFirstByDefaultBookTrueAndActiveTrue()).thenReturn(Optional.of(book));
    BillingCycleEntity cycle = new BillingCycleEntity();
    cycle.setCode("MONTHLY");
    cycle.setActive(true);
    when(billingCycleRepository.findById("MONTHLY")).thenReturn(Optional.of(cycle));
    when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(planPriceRepository.findByPriceBookIdAndPlanIdAndBillingCycleCode(
            "pb-in-smb", "starter", "MONTHLY"))
        .thenReturn(Optional.empty());
    when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> row =
        service.schedulePrice(
            "starter",
            Map.of(
                "billingCycleCode", "MONTHLY",
                "amountMinor", 149900,
                "effectiveAt", Instant.now().minusSeconds(60).toString()));

    assertEquals("APPLIED", row.get("status"));
    ArgumentCaptor<PlanPriceEntity> priceCap = ArgumentCaptor.forClass(PlanPriceEntity.class);
    verify(planPriceRepository).save(priceCap.capture());
    assertEquals(149900L, priceCap.getValue().getAmountMinor());
  }

  @Test
  void saveDraftRejectsPublished() {
    PlanVersionEntity published = new PlanVersionEntity();
    published.setId(1L);
    published.setPlanId("starter");
    published.setStatus("PUBLISHED");
    when(versionRepository.findByIdAndPlanId(1L, "starter")).thenReturn(Optional.of(published));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.saveDraft("starter", 1L, Map.of("label", "x")));
  }
}
