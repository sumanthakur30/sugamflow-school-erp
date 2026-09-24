package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.persistence.entity.PlanPriceEntity;
import com.sugamflow.school.subscription.persistence.entity.PriceBookEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionPaymentEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionLifecycleEntity;
import com.sugamflow.school.subscription.persistence.entity.UsageCounterEntity;
import com.sugamflow.school.subscription.persistence.repo.BillingCycleRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanPriceRepository;
import com.sugamflow.school.subscription.persistence.repo.PriceBookRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionInvoiceRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPaymentRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantAddonRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionLifecycleRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import com.sugamflow.school.subscription.persistence.repo.UsageCounterRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionAnalyticsServiceTest {

  @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
  @Mock private TenantSubscriptionLifecycleRepository lifecycleRepository;
  @Mock private SubscriptionInvoiceRepository invoiceRepository;
  @Mock private SubscriptionPaymentRepository paymentRepository;
  @Mock private PlanPriceRepository planPriceRepository;
  @Mock private PriceBookRepository priceBookRepository;
  @Mock private BillingCycleRepository billingCycleRepository;
  @Mock private UsageCounterRepository usageCounterRepository;
  @Mock private TenantAddonRepository tenantAddonRepository;

  private SubscriptionAnalyticsService service;

  @BeforeEach
  void setUp() {
    service =
        new SubscriptionAnalyticsService(
            tenantSubscriptionRepository,
            lifecycleRepository,
            invoiceRepository,
            paymentRepository,
            planPriceRepository,
            priceBookRepository,
            billingCycleRepository,
            usageCounterRepository,
            tenantAddonRepository);
  }

  @Test
  void overviewComputesMrrFromActiveMonthlyListPrices() {
    when(tenantSubscriptionRepository.count()).thenReturn(2L);
    when(lifecycleRepository.countByStatusIgnoreCase("ACTIVE")).thenReturn(2L);
    when(lifecycleRepository.countByStatusIgnoreCase("TRIAL")).thenReturn(0L);
    when(lifecycleRepository.countByStatusIgnoreCase("GRACE")).thenReturn(0L);
    when(lifecycleRepository.countByStatusIgnoreCase("EXPIRED")).thenReturn(0L);
    when(lifecycleRepository.countByStatusIgnoreCase("SUSPENDED")).thenReturn(0L);
    when(lifecycleRepository.countExpiringBetween(any(), any())).thenReturn(1L);
    when(paymentRepository.sumSuccessfulAmountSince(any())).thenReturn(50000L);
    when(invoiceRepository.sumIssuedOutstandingMinor()).thenReturn(10000L);
    when(invoiceRepository.countGroupedByStatus())
        .thenReturn(java.util.Collections.singletonList(new Object[] {"PAID", 1L}));
    when(tenantAddonRepository.count()).thenReturn(3L);
    when(paymentRepository.countByStatusIgnoreCase("SUCCESS")).thenReturn(4L);
    when(paymentRepository.countByStatusIgnoreCase("FAILED")).thenReturn(0L);

    PriceBookEntity book = new PriceBookEntity();
    book.setId("pb-in-smb");
    when(priceBookRepository.findFirstByDefaultBookTrueAndActiveTrue()).thenReturn(Optional.of(book));

    PlanPriceEntity starter = new PlanPriceEntity();
    starter.setPlanId("starter");
    starter.setBillingCycleCode("MONTHLY");
    starter.setAmountMinor(99900);
    starter.setActive(true);
    when(planPriceRepository.findByPriceBookIdOrderByPlanIdAscBillingCycleCodeAsc("pb-in-smb"))
        .thenReturn(List.of(starter));

    TenantSubscriptionEntity t1 = new TenantSubscriptionEntity();
    t1.setOrganizationId("A");
    t1.setPlanId("starter");
    TenantSubscriptionEntity t2 = new TenantSubscriptionEntity();
    t2.setOrganizationId("B");
    t2.setPlanId("starter");
    when(tenantSubscriptionRepository.findAll()).thenReturn(List.of(t1, t2));

    TenantSubscriptionLifecycleEntity l1 = new TenantSubscriptionLifecycleEntity();
    l1.setOrganizationId("A");
    l1.setStatus("ACTIVE");
    TenantSubscriptionLifecycleEntity l2 = new TenantSubscriptionLifecycleEntity();
    l2.setOrganizationId("B");
    l2.setStatus("ACTIVE");
    when(lifecycleRepository.findAll()).thenReturn(List.of(l1, l2));

    Map<String, Object> overview = service.overview();
    assertEquals(199800L, ((Number) overview.get("mrrMinor")).longValue());
    assertEquals(199800L * 12, ((Number) overview.get("arrMinor")).longValue());
    assertEquals(2L, ((Number) overview.get("pricedTenantCount")).longValue());
  }

  @Test
  void planMixAndUsageReturnRows() {
    TenantSubscriptionEntity t1 = new TenantSubscriptionEntity();
    t1.setOrganizationId("A");
    t1.setPlanId("starter");
    when(tenantSubscriptionRepository.findAll()).thenReturn(List.of(t1));
    TenantSubscriptionLifecycleEntity l1 = new TenantSubscriptionLifecycleEntity();
    l1.setOrganizationId("A");
    l1.setStatus("ACTIVE");
    when(lifecycleRepository.findAll()).thenReturn(List.of(l1));
    when(priceBookRepository.findFirstByDefaultBookTrueAndActiveTrue()).thenReturn(Optional.empty());

    List<Map<String, Object>> mix = service.planMix();
    assertEquals(1, mix.size());
    assertEquals("starter", mix.get(0).get("planId"));

    UsageCounterEntity u = new UsageCounterEntity();
    u.setOrganizationId("A");
    u.setLimitCode("students");
    u.setPeriodKey("ALL");
    u.setUsedValue(120);
    u.setUpdatedAt(Instant.now());
    when(usageCounterRepository.findTopUsed()).thenReturn(List.of(u));

    Map<String, Object> usage = service.usageHeatmap(10);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> top = (List<Map<String, Object>>) usage.get("top");
    assertEquals(1, top.size());
    assertEquals(120L, ((Number) top.get(0).get("usedValue")).longValue());
  }

  @Test
  void revenueBucketsByMonth() {
    SubscriptionPaymentEntity p = new SubscriptionPaymentEntity();
    p.setAmountMinor(1000);
    p.setStatus("SUCCESS");
    p.setCreatedAt(Instant.now());
    when(paymentRepository.findSuccessfulSince(any())).thenReturn(List.of(p));

    Map<String, Object> rev = service.revenue(1);
    assertEquals(1000L, ((Number) rev.get("totalCollectedMinor")).longValue());
  }
}
