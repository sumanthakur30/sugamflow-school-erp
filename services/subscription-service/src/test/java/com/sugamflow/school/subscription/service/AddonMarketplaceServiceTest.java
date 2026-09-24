package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.persistence.entity.AddonDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.AddonPriceEntity;
import com.sugamflow.school.subscription.persistence.entity.CreditWalletEntity;
import com.sugamflow.school.subscription.persistence.entity.PriceBookEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionInvoiceEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionInvoiceLineEntity;
import com.sugamflow.school.subscription.persistence.entity.TaxRuleEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantAddonEntity;
import com.sugamflow.school.subscription.persistence.repo.AddonDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.AddonPriceRepository;
import com.sugamflow.school.subscription.persistence.repo.CreditLedgerRepository;
import com.sugamflow.school.subscription.persistence.repo.CreditWalletRepository;
import com.sugamflow.school.subscription.persistence.repo.PriceBookRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionInvoiceLineRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionInvoiceRepository;
import com.sugamflow.school.subscription.persistence.repo.TaxRuleRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantAddonRepository;
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
class AddonMarketplaceServiceTest {

  private static final String ORG = "SCH-01";

  @Mock private AddonDefinitionRepository addonDefinitionRepository;
  @Mock private AddonPriceRepository addonPriceRepository;
  @Mock private TenantAddonRepository tenantAddonRepository;
  @Mock private CreditWalletRepository creditWalletRepository;
  @Mock private CreditLedgerRepository creditLedgerRepository;
  @Mock private PriceBookRepository priceBookRepository;
  @Mock private TaxRuleRepository taxRuleRepository;
  @Mock private SubscriptionInvoiceRepository invoiceRepository;
  @Mock private SubscriptionInvoiceLineRepository invoiceLineRepository;

  private AddonMarketplaceService service;

  @BeforeEach
  void setUp() {
    service =
        new AddonMarketplaceService(
            addonDefinitionRepository,
            addonPriceRepository,
            tenantAddonRepository,
            creditWalletRepository,
            creditLedgerRepository,
            priceBookRepository,
            taxRuleRepository,
            invoiceRepository,
            invoiceLineRepository);
  }

  @Test
  void createPurchaseDraftAppliesIntraStateGst() {
    when(priceBookRepository.findFirstByDefaultBookTrueAndActiveTrue())
        .thenReturn(Optional.of(book()));
    when(addonDefinitionRepository.findById("ADDON_AI_5K")).thenReturn(Optional.of(aiAddon()));
    when(addonPriceRepository.findBySkuAndPriceBookIdAndBillingCycleCodeAndActiveTrue(
            "ADDON_AI_5K", "pb-in-smb", "ONE_TIME"))
        .thenReturn(Optional.of(aiPrice()));
    when(taxRuleRepository.findFirstByDefaultRuleTrueAndActiveTrue()).thenReturn(Optional.of(gst18()));
    AtomicLong ids = new AtomicLong(1);
    when(invoiceRepository.save(any()))
        .thenAnswer(
            inv -> {
              SubscriptionInvoiceEntity e = inv.getArgument(0);
              if (e.getId() == null) {
                e.setId(ids.getAndIncrement());
              }
              return e;
            });
    when(invoiceLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());

    Map<String, Object> draft =
        service.createPurchaseDraft(
            ORG, Map.of("sku", "ADDON_AI_5K", "sellerStateCode", "09", "placeOfSupply", "09"));

    assertEquals("ONE_TIME", draft.get("billingCycleCode"));
    // 99900 + 9% + 9% = 99900 + 8991 + 8991 = 117882
    assertEquals(99900L, ((Number) draft.get("subtotalMinor")).longValue());
    assertEquals(17982L, ((Number) draft.get("taxMinor")).longValue());
    assertEquals(117882L, ((Number) draft.get("totalMinor")).longValue());
  }

  @Test
  void fulfillPaidInvoiceActivatesAddonAndCredits() {
    SubscriptionInvoiceEntity invoice = new SubscriptionInvoiceEntity();
    invoice.setId(42L);
    invoice.setOrganizationId(ORG);
    invoice.setStatus("PAID");
    invoice.setBillingCycleCode("ONE_TIME");

    SubscriptionInvoiceLineEntity line = new SubscriptionInvoiceLineEntity();
    line.setLineType("ADDON");
    line.setPlanId("ADDON_AI_5K");
    line.setQuantity(1);
    line.setAmountMinor(99900);

    when(tenantAddonRepository.findByInvoiceId(42L)).thenReturn(List.of());
    when(invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(42L)).thenReturn(List.of(line));
    when(addonDefinitionRepository.findById("ADDON_AI_5K")).thenReturn(Optional.of(aiAddon()));
    when(tenantAddonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(creditWalletRepository.findById(any())).thenReturn(Optional.empty());
    when(creditWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(creditLedgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> out = service.fulfillPaidInvoice(invoice);
    assertEquals(false, out.get("idempotent"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> credits = (List<Map<String, Object>>) out.get("credits");
    assertEquals(1, credits.size());
    assertEquals(5000L, ((Number) credits.get(0).get("delta")).longValue());

    ArgumentCaptor<CreditWalletEntity> walletCap = ArgumentCaptor.forClass(CreditWalletEntity.class);
    verify(creditWalletRepository).save(walletCap.capture());
    assertEquals(5000L, walletCap.getValue().getBalance());
    assertEquals("ai_credits", walletCap.getValue().getMeterCode());

    ArgumentCaptor<TenantAddonEntity> addonCap = ArgumentCaptor.forClass(TenantAddonEntity.class);
    verify(tenantAddonRepository).save(addonCap.capture());
    assertEquals("ADDON_AI_5K", addonCap.getValue().getSku());
    assertEquals("ACTIVE", addonCap.getValue().getStatus());
  }

  @Test
  void fulfillIsIdempotentWhenAddonAlreadyLinked() {
    SubscriptionInvoiceEntity invoice = new SubscriptionInvoiceEntity();
    invoice.setId(42L);
    TenantAddonEntity existing = new TenantAddonEntity();
    existing.setId(1L);
    existing.setSku("ADDON_AI_5K");
    existing.setStatus("ACTIVE");
    existing.setOrganizationId(ORG);
    when(tenantAddonRepository.findByInvoiceId(42L)).thenReturn(List.of(existing));

    Map<String, Object> out = service.fulfillPaidInvoice(invoice);
    assertTrue(Boolean.TRUE.equals(out.get("idempotent")));
  }

  private static PriceBookEntity book() {
    PriceBookEntity b = new PriceBookEntity();
    b.setId("pb-in-smb");
    b.setCode("IN_SMB");
    b.setActive(true);
    b.setDefaultBook(true);
    return b;
  }

  private static AddonDefinitionEntity aiAddon() {
    AddonDefinitionEntity d = new AddonDefinitionEntity();
    d.setSku("ADDON_AI_5K");
    d.setName("AI Credits 5,000");
    d.setAddonType("AI_CREDITS");
    d.setMeterCode("ai_credits");
    d.setCreditAmount(5000);
    d.setActive(true);
    return d;
  }

  private static AddonPriceEntity aiPrice() {
    AddonPriceEntity p = new AddonPriceEntity();
    p.setSku("ADDON_AI_5K");
    p.setPriceBookId("pb-in-smb");
    p.setBillingCycleCode("ONE_TIME");
    p.setAmountMinor(99900);
    p.setCurrency("INR");
    p.setActive(true);
    return p;
  }

  private static TaxRuleEntity gst18() {
    TaxRuleEntity t = new TaxRuleEntity();
    t.setId("tax-in-gst18");
    t.setCode("IN_GST_18");
    t.setCgstBps(900);
    t.setSgstBps(900);
    t.setIgstBps(1800);
    t.setActive(true);
    t.setDefaultRule(true);
    return t;
  }
}
