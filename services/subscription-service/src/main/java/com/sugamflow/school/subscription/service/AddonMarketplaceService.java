package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.persistence.entity.AddonDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.AddonPriceEntity;
import com.sugamflow.school.subscription.persistence.entity.CreditLedgerEntity;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Add-on marketplace: catalog, one-time purchase drafts, fulfill on invoice PAID (tenant_addon +
 * credit wallet). Does not change entitlements JSON contracts.
 */
@Service
public class AddonMarketplaceService {

  private final AddonDefinitionRepository addonDefinitionRepository;
  private final AddonPriceRepository addonPriceRepository;
  private final TenantAddonRepository tenantAddonRepository;
  private final CreditWalletRepository creditWalletRepository;
  private final CreditLedgerRepository creditLedgerRepository;
  private final PriceBookRepository priceBookRepository;
  private final TaxRuleRepository taxRuleRepository;
  private final SubscriptionInvoiceRepository invoiceRepository;
  private final SubscriptionInvoiceLineRepository invoiceLineRepository;

  public AddonMarketplaceService(
      AddonDefinitionRepository addonDefinitionRepository,
      AddonPriceRepository addonPriceRepository,
      TenantAddonRepository tenantAddonRepository,
      CreditWalletRepository creditWalletRepository,
      CreditLedgerRepository creditLedgerRepository,
      PriceBookRepository priceBookRepository,
      TaxRuleRepository taxRuleRepository,
      SubscriptionInvoiceRepository invoiceRepository,
      SubscriptionInvoiceLineRepository invoiceLineRepository) {
    this.addonDefinitionRepository = addonDefinitionRepository;
    this.addonPriceRepository = addonPriceRepository;
    this.tenantAddonRepository = tenantAddonRepository;
    this.creditWalletRepository = creditWalletRepository;
    this.creditLedgerRepository = creditLedgerRepository;
    this.priceBookRepository = priceBookRepository;
    this.taxRuleRepository = taxRuleRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
  }

  public List<Map<String, Object>> listCatalog(String priceBookId) {
    PriceBookEntity book = resolvePriceBook(priceBookId);
    Map<String, AddonPriceEntity> priceBySku = new LinkedHashMap<>();
    for (AddonPriceEntity p :
        addonPriceRepository.findByPriceBookIdAndActiveTrueOrderBySkuAsc(book.getId())) {
      if ("ONE_TIME".equalsIgnoreCase(p.getBillingCycleCode())) {
        priceBySku.putIfAbsent(p.getSku(), p);
      }
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (AddonDefinitionEntity def : addonDefinitionRepository.findByActiveTrueOrderBySortOrderAsc()) {
      Map<String, Object> row = addonToMap(def);
      AddonPriceEntity price = priceBySku.get(def.getSku());
      if (price != null) {
        row.put("priceBookId", price.getPriceBookId());
        row.put("billingCycleCode", price.getBillingCycleCode());
        row.put("amountMinor", price.getAmountMinor());
        row.put("currency", price.getCurrency());
        row.put("priced", true);
      } else {
        row.put("priced", false);
      }
      out.add(row);
    }
    return out;
  }

  public List<Map<String, Object>> listTenantAddons(String organizationId) {
    return tenantAddonRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(this::tenantAddonToMap)
        .toList();
  }

  public List<Map<String, Object>> listCreditWallets(String organizationId) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (CreditWalletEntity w :
        creditWalletRepository.findByOrganizationIdOrderByMeterCodeAsc(organizationId)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("organizationId", w.getOrganizationId());
      row.put("meterCode", w.getMeterCode());
      row.put("balance", w.getBalance());
      row.put("updatedAt", w.getUpdatedAt());
      out.add(row);
    }
    return out;
  }

  /**
   * Create a DRAFT invoice for one or more add-on SKUs (ONE_TIME + GST). Line type ADDON stores sku in
   * planId column.
   */
  @Transactional
  public Map<String, Object> createPurchaseDraft(String organizationId, Map<String, Object> body) {
    String sku = requireStr(body.get("sku"), "sku");
    int quantity = (int) Math.max(1, asLong(body.get("quantity"), 1));
    PriceBookEntity book = resolvePriceBook(str(body.get("priceBookId")));
    AddonDefinitionEntity def =
        addonDefinitionRepository
            .findById(sku)
            .filter(AddonDefinitionEntity::isActive)
            .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive addon sku: " + sku));
    AddonPriceEntity price =
        addonPriceRepository
            .findBySkuAndPriceBookIdAndBillingCycleCodeAndActiveTrue(sku, book.getId(), "ONE_TIME")
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No ONE_TIME price for sku=" + sku + " priceBook=" + book.getCode()));

    long unit = price.getAmountMinor();
    long subtotal = unit * quantity;
    Instant now = Instant.now();

    String sellerState = strOr(body.get("sellerStateCode"), "09");
    String placeOfSupply = strOr(body.get("placeOfSupply"), sellerState);
    TaxRuleEntity taxRule = resolveTaxRule(str(body.get("taxRuleId")));
    boolean interState = !sellerState.equalsIgnoreCase(placeOfSupply);
    long cgst = 0;
    long sgst = 0;
    long igst = 0;
    if (interState) {
      igst = bps(subtotal, taxRule.getIgstBps());
    } else {
      cgst = bps(subtotal, taxRule.getCgstBps());
      sgst = bps(subtotal, taxRule.getSgstBps());
    }
    long taxMinor = cgst + sgst + igst;

    SubscriptionInvoiceEntity invoice = new SubscriptionInvoiceEntity();
    invoice.setOrganizationId(organizationId);
    invoice.setStatus("DRAFT");
    invoice.setCurrency(price.getCurrency());
    invoice.setPlanId(null);
    invoice.setBillingCycleCode("ONE_TIME");
    invoice.setPriceBookId(book.getId());
    invoice.setSubtotalMinor(subtotal);
    invoice.setDiscountMinor(0);
    invoice.setTaxRuleId(taxRule.getId());
    invoice.setCgstMinor(cgst);
    invoice.setSgstMinor(sgst);
    invoice.setIgstMinor(igst);
    invoice.setTaxMinor(taxMinor);
    invoice.setTotalMinor(subtotal + taxMinor);
    invoice.setPlaceOfSupply(placeOfSupply);
    invoice.setSellerStateCode(sellerState);
    invoice.setProrationFactor(BigDecimal.ONE);
    invoice.setPeriodStart(now);
    invoice.setPeriodEnd(now);
    invoice.setNotes(strOr(body.get("notes"), "ADDON_PURCHASE:" + sku));
    invoice.setCreatedAt(now);
    invoice.setUpdatedAt(now);
    invoice = invoiceRepository.save(invoice);

    int sort = 10;
    saveLine(
        invoice.getId(),
        "ADDON",
        def.getName() + (quantity > 1 ? " × " + quantity : ""),
        quantity,
        unit,
        subtotal,
        sku,
        sort);
    sort += 10;
    if (cgst > 0) {
      saveLine(invoice.getId(), "TAX_CGST", "CGST " + (taxRule.getCgstBps() / 100.0) + "%", 1, cgst, cgst, null, sort);
      sort += 10;
    }
    if (sgst > 0) {
      saveLine(invoice.getId(), "TAX_SGST", "SGST " + (taxRule.getSgstBps() / 100.0) + "%", 1, sgst, sgst, null, sort);
      sort += 10;
    }
    if (igst > 0) {
      saveLine(invoice.getId(), "TAX_IGST", "IGST " + (taxRule.getIgstBps() / 100.0) + "%", 1, igst, igst, null, sort);
    }

    Map<String, Object> detail = invoiceDetail(invoice);
    detail.put("sku", sku);
    detail.put("quantity", quantity);
    return detail;
  }

  /** Idempotent fulfillment when invoice becomes PAID. */
  @Transactional
  public Map<String, Object> fulfillPaidInvoice(SubscriptionInvoiceEntity invoice) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("invoiceId", invoice.getId());
    List<Map<String, Object>> activated = new ArrayList<>();
    List<Map<String, Object>> credited = new ArrayList<>();

    List<TenantAddonEntity> existing = tenantAddonRepository.findByInvoiceId(invoice.getId());
    if (!existing.isEmpty()) {
      out.put("idempotent", true);
      out.put("addons", existing.stream().map(this::tenantAddonToMap).toList());
      return out;
    }

    Instant now = Instant.now();
    for (SubscriptionInvoiceLineEntity line :
        invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(invoice.getId())) {
      if (!"ADDON".equalsIgnoreCase(line.getLineType())) {
        continue;
      }
      String sku = line.getPlanId();
      if (sku == null || sku.isBlank()) {
        continue;
      }
      AddonDefinitionEntity def = addonDefinitionRepository.findById(sku).orElse(null);
      if (def == null) {
        continue;
      }

      TenantAddonEntity addon = new TenantAddonEntity();
      addon.setOrganizationId(invoice.getOrganizationId());
      addon.setSku(sku);
      addon.setQuantity(Math.max(1, line.getQuantity()));
      addon.setStatus("ACTIVE");
      addon.setInvoiceId(invoice.getId());
      addon.setStartsAt(now);
      addon.setCreatedAt(now);
      tenantAddonRepository.save(addon);
      activated.add(tenantAddonToMap(addon));

      if (def.getMeterCode() != null
          && !def.getMeterCode().isBlank()
          && def.getCreditAmount() > 0) {
        long delta = def.getCreditAmount() * Math.max(1, line.getQuantity());
        credited.add(credit(invoice.getOrganizationId(), def.getMeterCode(), delta, invoice.getId(), "Purchase " + sku));
      }
    }

    out.put("idempotent", false);
    out.put("addons", activated);
    out.put("credits", credited);
    return out;
  }

  private Map<String, Object> credit(
      String organizationId, String meterCode, long delta, Long invoiceId, String reason) {
    CreditWalletEntity.Pk pk = new CreditWalletEntity.Pk(organizationId, meterCode);
    CreditWalletEntity wallet =
        creditWalletRepository
            .findById(pk)
            .orElseGet(
                () -> {
                  CreditWalletEntity w = new CreditWalletEntity();
                  w.setOrganizationId(organizationId);
                  w.setMeterCode(meterCode);
                  w.setBalance(0);
                  return w;
                });
    long balance = wallet.getBalance() + delta;
    wallet.setBalance(balance);
    wallet.setUpdatedAt(Instant.now());
    creditWalletRepository.save(wallet);

    CreditLedgerEntity ledger = new CreditLedgerEntity();
    ledger.setOrganizationId(organizationId);
    ledger.setMeterCode(meterCode);
    ledger.setDelta(delta);
    ledger.setBalanceAfter(balance);
    ledger.setReason(reason);
    ledger.setInvoiceId(invoiceId);
    ledger.setCreatedAt(Instant.now());
    creditLedgerRepository.save(ledger);

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("meterCode", meterCode);
    row.put("delta", delta);
    row.put("balanceAfter", balance);
    return row;
  }

  private void saveLine(
      long invoiceId,
      String type,
      String description,
      int qty,
      long unit,
      long amount,
      String planId,
      int sort) {
    SubscriptionInvoiceLineEntity line = new SubscriptionInvoiceLineEntity();
    line.setInvoiceId(invoiceId);
    line.setLineType(type);
    line.setDescription(description);
    line.setQuantity(qty);
    line.setUnitAmountMinor(unit);
    line.setAmountMinor(amount);
    line.setPlanId(planId);
    line.setSortOrder(sort);
    invoiceLineRepository.save(line);
  }

  private Map<String, Object> invoiceDetail(SubscriptionInvoiceEntity invoice) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", invoice.getId());
    map.put("organizationId", invoice.getOrganizationId());
    map.put("invoiceNumber", invoice.getInvoiceNumber());
    map.put("status", invoice.getStatus());
    map.put("currency", invoice.getCurrency());
    map.put("planId", invoice.getPlanId());
    map.put("billingCycleCode", invoice.getBillingCycleCode());
    map.put("priceBookId", invoice.getPriceBookId());
    map.put("subtotalMinor", invoice.getSubtotalMinor());
    map.put("discountMinor", invoice.getDiscountMinor());
    map.put("taxRuleId", invoice.getTaxRuleId());
    map.put("cgstMinor", invoice.getCgstMinor());
    map.put("sgstMinor", invoice.getSgstMinor());
    map.put("igstMinor", invoice.getIgstMinor());
    map.put("taxMinor", invoice.getTaxMinor());
    map.put("totalMinor", invoice.getTotalMinor());
    map.put("placeOfSupply", invoice.getPlaceOfSupply());
    map.put("sellerStateCode", invoice.getSellerStateCode());
    map.put("notes", invoice.getNotes());
    map.put("gatewayOrderId", invoice.getGatewayOrderId());
    map.put("createdAt", invoice.getCreatedAt());
    List<Map<String, Object>> lines = new ArrayList<>();
    for (SubscriptionInvoiceLineEntity line :
        invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(invoice.getId())) {
      Map<String, Object> lm = new LinkedHashMap<>();
      lm.put("id", line.getId());
      lm.put("lineType", line.getLineType());
      lm.put("description", line.getDescription());
      lm.put("quantity", line.getQuantity());
      lm.put("unitAmountMinor", line.getUnitAmountMinor());
      lm.put("amountMinor", line.getAmountMinor());
      lm.put("planId", line.getPlanId());
      lm.put("sku", "ADDON".equalsIgnoreCase(line.getLineType()) ? line.getPlanId() : null);
      lm.put("sortOrder", line.getSortOrder());
      lines.add(lm);
    }
    map.put("lines", lines);
    return map;
  }

  private PriceBookEntity resolvePriceBook(String priceBookId) {
    if (priceBookId != null && !priceBookId.isBlank()) {
      return priceBookRepository
          .findById(priceBookId)
          .orElseThrow(() -> new IllegalArgumentException("Unknown priceBookId: " + priceBookId));
    }
    return priceBookRepository
        .findFirstByDefaultBookTrueAndActiveTrue()
        .orElseThrow(() -> new IllegalArgumentException("No default price book configured"));
  }

  private TaxRuleEntity resolveTaxRule(String taxRuleId) {
    if (taxRuleId != null && !taxRuleId.isBlank()) {
      return taxRuleRepository
          .findById(taxRuleId)
          .filter(TaxRuleEntity::isActive)
          .orElseThrow(() -> new IllegalArgumentException("Unknown taxRuleId: " + taxRuleId));
    }
    return taxRuleRepository
        .findFirstByDefaultRuleTrueAndActiveTrue()
        .orElseThrow(() -> new IllegalArgumentException("No default tax rule configured"));
  }

  private static long bps(long amount, int basisPoints) {
    return BigDecimal.valueOf(amount)
        .multiply(BigDecimal.valueOf(basisPoints))
        .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
        .longValue();
  }

  private Map<String, Object> addonToMap(AddonDefinitionEntity def) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("sku", def.getSku());
    map.put("name", def.getName());
    map.put("description", def.getDescription());
    map.put("addonType", def.getAddonType());
    map.put("meterCode", def.getMeterCode());
    map.put("creditAmount", def.getCreditAmount());
    map.put("sortOrder", def.getSortOrder());
    map.put("active", def.isActive());
    return map;
  }

  private Map<String, Object> tenantAddonToMap(TenantAddonEntity entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", entity.getId());
    map.put("organizationId", entity.getOrganizationId());
    map.put("sku", entity.getSku());
    map.put("quantity", entity.getQuantity());
    map.put("status", entity.getStatus());
    map.put("invoiceId", entity.getInvoiceId());
    map.put("startsAt", entity.getStartsAt());
    map.put("endsAt", entity.getEndsAt());
    map.put("createdAt", entity.getCreatedAt());
    return map;
  }

  private static String requireStr(Object v, String field) {
    String s = str(v);
    if (s == null || s.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return s.trim();
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v).trim();
  }

  private static String strOr(Object v, String fallback) {
    String s = str(v);
    return s == null || s.isBlank() ? fallback : s;
  }

  private static long asLong(Object v, long fallback) {
    if (v == null) {
      return fallback;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }
}
