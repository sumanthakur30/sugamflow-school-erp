package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.payment.SubscriptionRazorpayClient;
import com.sugamflow.school.subscription.persistence.entity.BillingCouponEntity;
import com.sugamflow.school.subscription.persistence.entity.BillingCycleEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanPriceEntity;
import com.sugamflow.school.subscription.persistence.entity.PriceBookEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionInvoiceEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionInvoiceLineEntity;
import com.sugamflow.school.subscription.persistence.entity.TaxRuleEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.repo.BillingCouponRepository;
import com.sugamflow.school.subscription.persistence.repo.BillingCycleRepository;
import com.sugamflow.school.subscription.persistence.repo.PlanPriceRepository;
import com.sugamflow.school.subscription.persistence.repo.PriceBookRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionInvoiceLineRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionInvoiceRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPaymentRepository;
import com.sugamflow.school.subscription.persistence.repo.TaxRuleRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
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
class CommercialBillingServiceTest {

  private static final String ORG = "SCH-01";

  @Mock private BillingCycleRepository billingCycleRepository;
  @Mock private PriceBookRepository priceBookRepository;
  @Mock private PlanPriceRepository planPriceRepository;
  @Mock private SubscriptionInvoiceRepository invoiceRepository;
  @Mock private SubscriptionInvoiceLineRepository invoiceLineRepository;
  @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
  @Mock private SubscriptionService subscriptionService;
  @Mock private TaxRuleRepository taxRuleRepository;
  @Mock private BillingCouponRepository couponRepository;
  @Mock private SubscriptionPaymentRepository paymentRepository;
  @Mock private SubscriptionLifecycleService lifecycleService;
  @Mock private SubscriptionRazorpayClient razorpayClient;
  @Mock private AddonMarketplaceService addonMarketplaceService;

  private CommercialBillingService service;

  @BeforeEach
  void setUp() {
    service =
        new CommercialBillingService(
            billingCycleRepository,
            priceBookRepository,
            planPriceRepository,
            invoiceRepository,
            invoiceLineRepository,
            tenantSubscriptionRepository,
            subscriptionService,
            taxRuleRepository,
            couponRepository,
            paymentRepository,
            lifecycleService,
            razorpayClient,
            addonMarketplaceService,
            new ObjectMapper());
  }

  @Test
  void createDraftInvoiceAppliesIntraStateGstAndCoupon() {
    stubDraftLookups();
    when(couponRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(welcomeCoupon()));
    when(invoiceRepository.save(any()))
        .thenAnswer(
            inv -> {
              SubscriptionInvoiceEntity e = inv.getArgument(0);
              e.setId(42L);
              return e;
            });
    when(invoiceLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(42L)).thenReturn(List.of());

    Map<String, Object> out =
        service.createDraftInvoice(
            ORG, Map.of("billingCycleCode", "MONTHLY", "couponCode", "WELCOME10"));

    assertEquals("DRAFT", out.get("status"));
    assertEquals(99900L, out.get("subtotalMinor"));
    assertEquals(9990L, out.get("discountMinor"));
    assertEquals(8092L, out.get("cgstMinor"));
    assertEquals(8092L, out.get("sgstMinor"));
    assertEquals(16184L, out.get("taxMinor"));
    assertEquals(106094L, out.get("totalMinor"));

    ArgumentCaptor<SubscriptionInvoiceLineEntity> lines =
        ArgumentCaptor.forClass(SubscriptionInvoiceLineEntity.class);
    verify(invoiceLineRepository, atLeastOnce()).save(lines.capture());
    assertTrue(lines.getAllValues().stream().anyMatch(l -> "TAX_CGST".equals(l.getLineType())));
    assertTrue(lines.getAllValues().stream().anyMatch(l -> "DISCOUNT".equals(l.getLineType())));
  }

  @Test
  void createDraftInvoiceProratesRemainingDays() {
    stubDraftLookups();
    when(invoiceRepository.save(any()))
        .thenAnswer(
            inv -> {
              SubscriptionInvoiceEntity e = inv.getArgument(0);
              e.setId(43L);
              return e;
            });
    when(invoiceLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(43L)).thenReturn(List.of());

    Map<String, Object> out =
        service.createDraftInvoice(ORG, Map.of("billingCycleCode", "MONTHLY", "remainingDays", 15));

    assertTrue(((Number) out.get("subtotalMinor")).longValue() < 99900L);
    assertTrue(((Number) out.get("prorationFactor")).doubleValue() < 1.0);
  }

  @Test
  void paymentWebhookMarksPaidAndRenews() {
    SubscriptionInvoiceEntity issued = new SubscriptionInvoiceEntity();
    issued.setId(7L);
    issued.setOrganizationId(ORG);
    issued.setStatus("ISSUED");
    issued.setTotalMinor(100000);
    issued.setCurrency("INR");
    issued.setBillingCycleCode("MONTHLY");
    when(invoiceRepository.findByIdAndOrganizationId(7L, ORG)).thenReturn(Optional.of(issued));
    when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByProviderAndProviderPaymentId("RAZORPAY", "pay_1"))
        .thenReturn(Optional.empty());
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(billingCycleRepository.findById("MONTHLY")).thenReturn(Optional.of(monthly()));
    when(lifecycleService.renew(eq(ORG), any())).thenReturn(Map.of("resolvedStatus", "ACTIVE"));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());
    when(addonMarketplaceService.fulfillPaidInvoice(any())).thenReturn(Map.of("addons", List.of()));

    Map<String, Object> out =
        service.applyPaymentWebhook(
            Map.of(
                "organizationId", ORG,
                "invoiceId", 7,
                "status", "SUCCESS",
                "provider", "RAZORPAY",
                "providerPaymentId", "pay_1",
                "amountMinor", 100000));

    assertEquals(true, out.get("renewed"));
    assertEquals("PAID", issued.getStatus());
    verify(lifecycleService).renew(eq(ORG), any());
    verify(addonMarketplaceService).fulfillPaidInvoice(any());
  }

  @Test
  void oneTimeInvoiceSkipsRenewAndCreatesSimulatedOrder() {
    SubscriptionInvoiceEntity oneTime = new SubscriptionInvoiceEntity();
    oneTime.setId(9L);
    oneTime.setOrganizationId(ORG);
    oneTime.setStatus("ISSUED");
    oneTime.setTotalMinor(35282);
    oneTime.setCurrency("INR");
    oneTime.setBillingCycleCode("ONE_TIME");
    when(invoiceRepository.findByIdAndOrganizationId(9L, ORG)).thenReturn(Optional.of(oneTime));
    when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(paymentRepository.findByProviderAndProviderPaymentId("MANUAL", "m1"))
        .thenReturn(Optional.empty());
    when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(9L)).thenReturn(List.of());
    when(addonMarketplaceService.fulfillPaidInvoice(any()))
        .thenReturn(Map.of("addons", List.of(Map.of("sku", "ADDON_AI_5K"))));

    Map<String, Object> paid =
        service.applyPaymentWebhook(
            Map.of(
                "organizationId", ORG,
                "invoiceId", 9,
                "status", "SUCCESS",
                "provider", "MANUAL",
                "providerPaymentId", "m1",
                "amountMinor", 35282));
    assertEquals(false, paid.get("renewed"));
    assertEquals(true, paid.get("marketplaceFulfilled"));
    verify(lifecycleService, org.mockito.Mockito.never()).renew(any(), any());

    when(razorpayClient.createOrder(eq(35282L), eq("INR"), any(), any()))
        .thenReturn(
            Map.of(
                "checkoutMode", "SIMULATED",
                "provider", "SIMULATED",
                "razorpayKeyId", "rzp_test_simulated",
                "razorpayOrderId", "order_sim_abc",
                "amountMinor", 35282L,
                "currency", "INR"));
    SubscriptionInvoiceEntity forOrder = new SubscriptionInvoiceEntity();
    forOrder.setId(10L);
    forOrder.setOrganizationId(ORG);
    forOrder.setStatus("DRAFT");
    forOrder.setTotalMinor(35282);
    forOrder.setCurrency("INR");
    when(invoiceRepository.findByIdAndOrganizationId(10L, ORG)).thenReturn(Optional.of(forOrder));

    Map<String, Object> order = service.createRazorpayOrder(ORG, 10L);
    assertEquals("order_sim_abc", order.get("razorpayOrderId"));
    assertEquals("ISSUED", forOrder.getStatus());
    assertEquals("order_sim_abc", forOrder.getGatewayOrderId());
  }

  @Test
  void issueInvoiceRejectsNonDraft() {
    SubscriptionInvoiceEntity issued = new SubscriptionInvoiceEntity();
    issued.setId(8L);
    issued.setOrganizationId(ORG);
    issued.setStatus("ISSUED");
    when(invoiceRepository.findByIdAndOrganizationId(8L, ORG)).thenReturn(Optional.of(issued));

    assertThrows(IllegalArgumentException.class, () -> service.issueInvoice(ORG, 8L));
  }

  private void stubDraftLookups() {
    when(billingCycleRepository.findById("MONTHLY")).thenReturn(Optional.of(monthly()));
    when(priceBookRepository.findFirstByDefaultBookTrueAndActiveTrue())
        .thenReturn(Optional.of(defaultBook()));
    TenantSubscriptionEntity sub = new TenantSubscriptionEntity();
    sub.setOrganizationId(ORG);
    sub.setPlanId("starter");
    when(tenantSubscriptionRepository.findById(ORG)).thenReturn(Optional.of(sub));
    when(subscriptionService.getPlan("starter")).thenReturn(starterPlan());
    when(planPriceRepository.findByPriceBookIdAndPlanIdAndBillingCycleCode(
            "pb-in-smb", "starter", "MONTHLY"))
        .thenReturn(Optional.of(starterMonthlyPrice()));
    when(taxRuleRepository.findFirstByDefaultRuleTrueAndActiveTrue())
        .thenReturn(Optional.of(gst18()));
  }

  private static BillingCycleEntity monthly() {
    BillingCycleEntity c = new BillingCycleEntity();
    c.setCode("MONTHLY");
    c.setName("Monthly");
    c.setMonths(1);
    c.setActive(true);
    return c;
  }

  private static PriceBookEntity defaultBook() {
    PriceBookEntity b = new PriceBookEntity();
    b.setId("pb-in-smb");
    b.setCode("IN_SMB");
    b.setName("India SMB");
    b.setCurrency("INR");
    b.setActive(true);
    b.setDefaultBook(true);
    return b;
  }

  private static PlanPriceEntity starterMonthlyPrice() {
    PlanPriceEntity p = new PlanPriceEntity();
    p.setId(1L);
    p.setPriceBookId("pb-in-smb");
    p.setPlanId("starter");
    p.setBillingCycleCode("MONTHLY");
    p.setAmountMinor(99900);
    p.setCurrency("INR");
    p.setActive(true);
    return p;
  }

  private static TaxRuleEntity gst18() {
    TaxRuleEntity t = new TaxRuleEntity();
    t.setId("tax-in-gst18");
    t.setCode("IN_GST_18");
    t.setName("India GST 18%");
    t.setCgstBps(900);
    t.setSgstBps(900);
    t.setIgstBps(1800);
    t.setActive(true);
    t.setDefaultRule(true);
    return t;
  }

  private static BillingCouponEntity welcomeCoupon() {
    BillingCouponEntity c = new BillingCouponEntity();
    c.setId("cpn-welcome10");
    c.setCode("WELCOME10");
    c.setName("Welcome");
    c.setDiscountType("PERCENT");
    c.setDiscountValue(10);
    c.setActive(true);
    c.setMinSubtotalMinor(0);
    c.setRedemptionCount(0);
    return c;
  }

  private static SubscriptionPlan starterPlan() {
    SubscriptionPlan plan = new SubscriptionPlan();
    plan.setId("starter");
    plan.setCode("starter");
    plan.setName("Starter");
    plan.setPlanType("STANDARD");
    plan.setActive(true);
    return plan;
  }
}
