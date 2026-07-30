package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.payment.SubscriptionRazorpayClient;
import com.sugamflow.school.subscription.persistence.entity.BillingCouponEntity;
import com.sugamflow.school.subscription.persistence.entity.BillingCycleEntity;
import com.sugamflow.school.subscription.persistence.entity.PlanPriceEntity;
import com.sugamflow.school.subscription.persistence.entity.PriceBookEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionInvoiceEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionInvoiceLineEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionPaymentEntity;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commercial billing: price books, GST, coupons, proration, draft/issue/void/paid invoices.
 * Does not change entitlements/feature-flag JSON contracts.
 */
@Service
public class CommercialBillingService {

  private final BillingCycleRepository billingCycleRepository;
  private final PriceBookRepository priceBookRepository;
  private final PlanPriceRepository planPriceRepository;
  private final SubscriptionInvoiceRepository invoiceRepository;
  private final SubscriptionInvoiceLineRepository invoiceLineRepository;
  private final TenantSubscriptionRepository tenantSubscriptionRepository;
  private final SubscriptionService subscriptionService;
  private final TaxRuleRepository taxRuleRepository;
  private final BillingCouponRepository couponRepository;
  private final SubscriptionPaymentRepository paymentRepository;
  private final SubscriptionLifecycleService lifecycleService;
  private final SubscriptionRazorpayClient razorpayClient;
  private final AddonMarketplaceService addonMarketplaceService;
  private final ObjectMapper objectMapper;

  public CommercialBillingService(
      BillingCycleRepository billingCycleRepository,
      PriceBookRepository priceBookRepository,
      PlanPriceRepository planPriceRepository,
      SubscriptionInvoiceRepository invoiceRepository,
      SubscriptionInvoiceLineRepository invoiceLineRepository,
      TenantSubscriptionRepository tenantSubscriptionRepository,
      SubscriptionService subscriptionService,
      TaxRuleRepository taxRuleRepository,
      BillingCouponRepository couponRepository,
      SubscriptionPaymentRepository paymentRepository,
      SubscriptionLifecycleService lifecycleService,
      SubscriptionRazorpayClient razorpayClient,
      AddonMarketplaceService addonMarketplaceService,
      ObjectMapper objectMapper) {
    this.billingCycleRepository = billingCycleRepository;
    this.priceBookRepository = priceBookRepository;
    this.planPriceRepository = planPriceRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.tenantSubscriptionRepository = tenantSubscriptionRepository;
    this.subscriptionService = subscriptionService;
    this.taxRuleRepository = taxRuleRepository;
    this.couponRepository = couponRepository;
    this.paymentRepository = paymentRepository;
    this.lifecycleService = lifecycleService;
    this.razorpayClient = razorpayClient;
    this.addonMarketplaceService = addonMarketplaceService;
    this.objectMapper = objectMapper;
  }

  public List<Map<String, Object>> listBillingCycles() {
    return billingCycleRepository.findByActiveTrueOrderBySortOrderAsc().stream()
        .map(this::cycleToMap)
        .toList();
  }

  public List<Map<String, Object>> listPriceBooks() {
    return priceBookRepository.findAllByOrderByCodeAsc().stream().map(this::priceBookToMap).toList();
  }

  @Transactional
  public Map<String, Object> upsertPriceBook(Map<String, Object> body) {
    String id = str(body.get("id"));
    String code = requireStr(body.get("code"), "code");
    PriceBookEntity entity =
        id != null && !id.isBlank()
            ? priceBookRepository.findById(id).orElseGet(PriceBookEntity::new)
            : priceBookRepository.findByCodeIgnoreCase(code).orElseGet(PriceBookEntity::new);

    if (entity.getId() == null || entity.getId().isBlank()) {
      entity.setId(id != null && !id.isBlank() ? id : "pb-" + UUID.randomUUID().toString().substring(0, 8));
      entity.setCreatedAt(Instant.now());
    }
    entity.setCode(code.trim().toUpperCase(Locale.ROOT));
    entity.setName(requireStr(body.get("name"), "name"));
    entity.setCurrency(strOr(body.get("currency"), entity.getCurrency() == null ? "INR" : entity.getCurrency()));
    if (body.containsKey("active")) {
      entity.setActive(asBool(body.get("active"), true));
    }
    if (body.containsKey("isDefault") || body.containsKey("default")) {
      boolean makeDefault =
          asBool(body.containsKey("isDefault") ? body.get("isDefault") : body.get("default"), false);
      if (makeDefault) {
        clearDefaultBooks();
      }
      entity.setDefaultBook(makeDefault);
    }
    if (body.containsKey("notes")) {
      entity.setNotes(str(body.get("notes")));
    }
    entity.setUpdatedAt(Instant.now());
    return priceBookToMap(priceBookRepository.save(entity));
  }

  public List<Map<String, Object>> listPlanPrices(String priceBookId) {
    requirePriceBook(priceBookId);
    return planPriceRepository.findByPriceBookIdOrderByPlanIdAscBillingCycleCodeAsc(priceBookId).stream()
        .map(this::planPriceToMap)
        .toList();
  }

  public List<Map<String, Object>> listPricesForPlan(String planId) {
    return planPriceRepository.findByPlanIdAndActiveTrueOrderByBillingCycleCodeAsc(planId).stream()
        .map(this::planPriceToMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> upsertPlanPrice(Map<String, Object> body) {
    String priceBookId = requireStr(body.get("priceBookId"), "priceBookId");
    String planId = requireStr(body.get("planId"), "planId");
    String cycle = requireStr(body.get("billingCycleCode"), "billingCycleCode").toUpperCase(Locale.ROOT);
    requirePriceBook(priceBookId);
    requireCycle(cycle);
    if (subscriptionService.getPlan(planId) == null) {
      throw new IllegalArgumentException("Unknown planId: " + planId);
    }
    long amountMinor = asLong(body.get("amountMinor"), -1);
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amountMinor must be >= 0 (paise/minor units)");
    }

    PlanPriceEntity entity =
        planPriceRepository
            .findByPriceBookIdAndPlanIdAndBillingCycleCode(priceBookId, planId, cycle)
            .orElseGet(PlanPriceEntity::new);
    entity.setPriceBookId(priceBookId);
    entity.setPlanId(planId);
    entity.setBillingCycleCode(cycle);
    entity.setAmountMinor(amountMinor);
    entity.setCurrency(strOr(body.get("currency"), "INR"));
    if (body.containsKey("active")) {
      entity.setActive(asBool(body.get("active"), true));
    } else if (entity.getId() == null) {
      entity.setActive(true);
    }
    entity.setUpdatedAt(Instant.now());
    return planPriceToMap(planPriceRepository.save(entity));
  }

  public List<Map<String, Object>> listTaxRules() {
    return taxRuleRepository.findAllByOrderByCodeAsc().stream().map(this::taxRuleToMap).toList();
  }

  @Transactional
  public Map<String, Object> upsertTaxRule(Map<String, Object> body) {
    String id = str(body.get("id"));
    String code = requireStr(body.get("code"), "code");
    TaxRuleEntity entity =
        id != null && !id.isBlank()
            ? taxRuleRepository.findById(id).orElseGet(TaxRuleEntity::new)
            : taxRuleRepository.findByCodeIgnoreCase(code).orElseGet(TaxRuleEntity::new);
    if (entity.getId() == null || entity.getId().isBlank()) {
      entity.setId(id != null && !id.isBlank() ? id : "tax-" + UUID.randomUUID().toString().substring(0, 8));
      entity.setCreatedAt(Instant.now());
    }
    entity.setCode(code.trim().toUpperCase(Locale.ROOT));
    entity.setName(requireStr(body.get("name"), "name"));
    entity.setCountryCode(strOr(body.get("countryCode"), "IN"));
    entity.setHsnSac(str(body.get("hsnSac")));
    entity.setCgstBps((int) asLong(body.get("cgstBps"), entity.getCgstBps()));
    entity.setSgstBps((int) asLong(body.get("sgstBps"), entity.getSgstBps()));
    entity.setIgstBps((int) asLong(body.get("igstBps"), entity.getIgstBps()));
    if (body.containsKey("active")) {
      entity.setActive(asBool(body.get("active"), true));
    }
    if (body.containsKey("isDefault")) {
      boolean makeDefault = asBool(body.get("isDefault"), false);
      if (makeDefault) {
        for (TaxRuleEntity existing : taxRuleRepository.findAll()) {
          if (existing.isDefaultRule()) {
            existing.setDefaultRule(false);
            existing.setUpdatedAt(Instant.now());
            taxRuleRepository.save(existing);
          }
        }
      }
      entity.setDefaultRule(makeDefault);
    }
    if (body.containsKey("notes")) {
      entity.setNotes(str(body.get("notes")));
    }
    entity.setUpdatedAt(Instant.now());
    return taxRuleToMap(taxRuleRepository.save(entity));
  }

  public List<Map<String, Object>> listCoupons() {
    return couponRepository.findAllByOrderByCodeAsc().stream().map(this::couponToMap).toList();
  }

  @Transactional
  public Map<String, Object> upsertCoupon(Map<String, Object> body) {
    String id = str(body.get("id"));
    String code = requireStr(body.get("code"), "code").toUpperCase(Locale.ROOT);
    BillingCouponEntity entity =
        id != null && !id.isBlank()
            ? couponRepository.findById(id).orElseGet(BillingCouponEntity::new)
            : couponRepository.findByCodeIgnoreCase(code).orElseGet(BillingCouponEntity::new);
    if (entity.getId() == null || entity.getId().isBlank()) {
      entity.setId(id != null && !id.isBlank() ? id : "cpn-" + UUID.randomUUID().toString().substring(0, 8));
      entity.setCreatedAt(Instant.now());
      entity.setRedemptionCount(0);
    }
    entity.setCode(code);
    entity.setName(requireStr(body.get("name"), "name"));
    String type = requireStr(body.get("discountType"), "discountType").toUpperCase(Locale.ROOT);
    if (!"PERCENT".equals(type) && !"FIXED_MINOR".equals(type)) {
      throw new IllegalArgumentException("discountType must be PERCENT or FIXED_MINOR");
    }
    entity.setDiscountType(type);
    long value = asLong(body.get("discountValue"), -1);
    if (value < 0) {
      throw new IllegalArgumentException("discountValue must be >= 0");
    }
    if ("PERCENT".equals(type) && value > 100) {
      throw new IllegalArgumentException("PERCENT discountValue must be 0..100");
    }
    entity.setDiscountValue(value);
    entity.setCurrency(strOr(body.get("currency"), "INR"));
    if (body.containsKey("maxRedemptions")) {
      Object max = body.get("maxRedemptions");
      entity.setMaxRedemptions(max == null || String.valueOf(max).isBlank() ? null : (int) asLong(max, 0));
    }
    entity.setMinSubtotalMinor(asLong(body.get("minSubtotalMinor"), entity.getMinSubtotalMinor()));
    if (body.containsKey("applicablePlanId")) {
      entity.setApplicablePlanId(str(body.get("applicablePlanId")));
    }
    if (body.containsKey("active")) {
      entity.setActive(asBool(body.get("active"), true));
    }
    if (body.containsKey("notes")) {
      entity.setNotes(str(body.get("notes")));
    }
    entity.setUpdatedAt(Instant.now());
    return couponToMap(couponRepository.save(entity));
  }

  @Transactional
  public Map<String, Object> createDraftInvoice(String organizationId, Map<String, Object> body) {
    String cycle = strOr(body.get("billingCycleCode"), "MONTHLY").toUpperCase(Locale.ROOT);
    BillingCycleEntity cycleEntity = requireCycle(cycle);
    PriceBookEntity book = resolvePriceBook(str(body.get("priceBookId")));

    String assignedPlanId =
        tenantSubscriptionRepository
            .findById(organizationId)
            .map(TenantSubscriptionEntity::getPlanId)
            .orElse("starter");
    SubscriptionPlan plan = subscriptionService.getPlan(assignedPlanId);
    if (plan == null) {
      plan = subscriptionService.getPlan("starter");
    }
    if (plan == null) {
      throw new IllegalArgumentException("No plan assigned for organization " + organizationId);
    }
    final String planId = plan.getId();

    PlanPriceEntity price =
        planPriceRepository
            .findByPriceBookIdAndPlanIdAndBillingCycleCode(book.getId(), planId, cycle)
            .filter(PlanPriceEntity::isActive)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No active price for plan="
                            + planId
                            + " cycle="
                            + cycle
                            + " priceBook="
                            + book.getCode()));

    Instant now = Instant.now();
    long fullAmount = price.getAmountMinor();
    BigDecimal prorationFactor = BigDecimal.ONE;
    Instant periodStart = now;
    Instant periodEnd = now.atZone(ZoneOffset.UTC).plusMonths(cycleEntity.getMonths()).toInstant();

    // Proration: remainingDays within full cycle window.
    if (body.get("remainingDays") != null) {
      long remainingDays = asLong(body.get("remainingDays"), -1);
      long fullDays = Math.max(1, ChronoUnit.DAYS.between(now, periodEnd));
      if (remainingDays < 0) {
        throw new IllegalArgumentException("remainingDays must be >= 0");
      }
      remainingDays = Math.min(remainingDays, fullDays);
      prorationFactor =
          BigDecimal.valueOf(remainingDays)
              .divide(BigDecimal.valueOf(fullDays), 8, RoundingMode.HALF_UP);
      periodEnd = now.plus(remainingDays, ChronoUnit.DAYS);
    } else if (body.get("periodStart") != null && body.get("periodEnd") != null) {
      Instant ps = Instant.parse(String.valueOf(body.get("periodStart")));
      Instant pe = Instant.parse(String.valueOf(body.get("periodEnd")));
      if (!pe.isAfter(ps)) {
        throw new IllegalArgumentException("periodEnd must be after periodStart");
      }
      periodStart = ps;
      periodEnd = pe;
      long fullDays = Math.max(1, ChronoUnit.DAYS.between(now, now.atZone(ZoneOffset.UTC).plusMonths(cycleEntity.getMonths()).toInstant()));
      long usedDays = Math.max(1, ChronoUnit.DAYS.between(ps, pe));
      prorationFactor =
          BigDecimal.valueOf(Math.min(usedDays, fullDays))
              .divide(BigDecimal.valueOf(fullDays), 8, RoundingMode.HALF_UP);
    }

    long planLineAmount =
        BigDecimal.valueOf(fullAmount).multiply(prorationFactor).setScale(0, RoundingMode.HALF_UP).longValue();

    long discountMinor = 0;
    String couponCode = str(body.get("couponCode"));
    BillingCouponEntity coupon = null;
    if (couponCode != null && !couponCode.isBlank()) {
      coupon = requireValidCoupon(couponCode, planId, planLineAmount, now);
      discountMinor = computeDiscount(coupon, planLineAmount);
    }
    long taxable = Math.max(0, planLineAmount - discountMinor);

    String sellerState = strOr(body.get("sellerStateCode"), "09");
    String placeOfSupply = strOr(body.get("placeOfSupply"), sellerState);
    TaxRuleEntity taxRule = resolveTaxRule(str(body.get("taxRuleId")));
    boolean interState = !sellerState.equalsIgnoreCase(placeOfSupply);
    long cgst = 0;
    long sgst = 0;
    long igst = 0;
    if (interState) {
      igst = bps(taxable, taxRule.getIgstBps());
    } else {
      cgst = bps(taxable, taxRule.getCgstBps());
      sgst = bps(taxable, taxRule.getSgstBps());
    }
    long taxMinor = cgst + sgst + igst;

    SubscriptionInvoiceEntity invoice = new SubscriptionInvoiceEntity();
    invoice.setOrganizationId(organizationId);
    invoice.setStatus("DRAFT");
    invoice.setCurrency(price.getCurrency());
    invoice.setPlanId(planId);
    invoice.setBillingCycleCode(cycle);
    invoice.setPriceBookId(book.getId());
    invoice.setSubtotalMinor(planLineAmount);
    invoice.setDiscountMinor(discountMinor);
    invoice.setCouponCode(coupon == null ? null : coupon.getCode());
    invoice.setTaxRuleId(taxRule.getId());
    invoice.setCgstMinor(cgst);
    invoice.setSgstMinor(sgst);
    invoice.setIgstMinor(igst);
    invoice.setTaxMinor(taxMinor);
    invoice.setTotalMinor(taxable + taxMinor);
    invoice.setPlaceOfSupply(placeOfSupply);
    invoice.setSellerStateCode(sellerState);
    invoice.setProrationFactor(prorationFactor);
    invoice.setPeriodStart(periodStart);
    invoice.setPeriodEnd(periodEnd);
    invoice.setNotes(str(body.get("notes")));
    invoice.setCreatedAt(now);
    invoice.setUpdatedAt(now);
    invoice = invoiceRepository.save(invoice);

    int sort = 10;
    saveLine(
        invoice.getId(),
        "PLAN",
        plan.getName()
            + " · "
            + cycleEntity.getName()
            + (prorationFactor.compareTo(BigDecimal.ONE) < 0 ? " (prorated)" : ""),
        1,
        planLineAmount,
        planLineAmount,
        planId,
        sort);
    sort += 10;
    if (discountMinor > 0 && coupon != null) {
      saveLine(
          invoice.getId(),
          "DISCOUNT",
          "Coupon " + coupon.getCode(),
          1,
          -discountMinor,
          -discountMinor,
          null,
          sort);
      sort += 10;
    }
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

    return invoiceDetail(invoice);
  }

  public List<Map<String, Object>> listInvoices(String organizationId) {
    return invoiceRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(this::invoiceSummary)
        .toList();
  }

  public Map<String, Object> getInvoice(String organizationId, long invoiceId) {
    return invoiceDetail(requireInvoice(organizationId, invoiceId));
  }

  @Transactional
  public Map<String, Object> issueInvoice(String organizationId, long invoiceId) {
    SubscriptionInvoiceEntity invoice = requireInvoice(organizationId, invoiceId);
    if (!"DRAFT".equalsIgnoreCase(invoice.getStatus())) {
      throw new IllegalArgumentException("Only DRAFT invoices can be issued (status=" + invoice.getStatus() + ")");
    }
    Instant now = Instant.now();
    invoice.setStatus("ISSUED");
    invoice.setIssuedAt(now);
    invoice.setDueAt(now.plus(7, ChronoUnit.DAYS));
    if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
      invoice.setInvoiceNumber(nextInvoiceNumber(now));
    }
    invoice.setUpdatedAt(now);
    return invoiceDetail(invoiceRepository.save(invoice));
  }

  @Transactional
  public Map<String, Object> voidInvoice(String organizationId, long invoiceId, String notes) {
    SubscriptionInvoiceEntity invoice = requireInvoice(organizationId, invoiceId);
    if ("VOID".equalsIgnoreCase(invoice.getStatus())) {
      return invoiceDetail(invoice);
    }
    if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
      throw new IllegalArgumentException("Cannot void a PAID invoice");
    }
    invoice.setStatus("VOID");
    if (notes != null && !notes.isBlank()) {
      invoice.setNotes(notes);
    }
    invoice.setUpdatedAt(Instant.now());
    return invoiceDetail(invoiceRepository.save(invoice));
  }

  /**
   * Payment webhook / manual capture. Marks invoice PAID, records payment, renews lifecycle, bumps coupon
   * redemption when applicable.
   */
  @Transactional
  public Map<String, Object> applyPaymentWebhook(Map<String, Object> body) {
    String organizationId = requireStr(body.get("organizationId"), "organizationId");
    long invoiceId = asLong(body.get("invoiceId"), -1);
    if (invoiceId < 0) {
      throw new IllegalArgumentException("invoiceId is required");
    }
    String status = requireStr(body.get("status"), "status").toUpperCase(Locale.ROOT);
    String provider = strOr(body.get("provider"), "MANUAL");
    String providerPaymentId = str(body.get("providerPaymentId"));

    if (providerPaymentId != null && !providerPaymentId.isBlank()) {
      var existing = paymentRepository.findByProviderAndProviderPaymentId(provider, providerPaymentId);
      if (existing.isPresent()) {
        Map<String, Object> idempotent = new LinkedHashMap<>();
        idempotent.put("idempotent", true);
        idempotent.put("paymentId", existing.get().getId());
        idempotent.put("invoice", getInvoice(organizationId, invoiceId));
        return idempotent;
      }
    }

    SubscriptionInvoiceEntity invoice = requireInvoice(organizationId, invoiceId);
    if ("VOID".equalsIgnoreCase(invoice.getStatus())) {
      throw new IllegalArgumentException("Cannot pay a VOID invoice");
    }

    String gatewayOrderId = str(body.get("gatewayOrderId"));
    if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
      gatewayOrderId = invoice.getGatewayOrderId();
    }

    SubscriptionPaymentEntity payment = new SubscriptionPaymentEntity();
    payment.setOrganizationId(organizationId);
    payment.setInvoiceId(invoiceId);
    payment.setProvider(provider);
    payment.setProviderPaymentId(providerPaymentId);
    payment.setGatewayOrderId(gatewayOrderId);
    payment.setAmountMinor(asLong(body.get("amountMinor"), invoice.getTotalMinor()));
    payment.setCurrency(strOr(body.get("currency"), invoice.getCurrency()));
    payment.setStatus(status);
    payment.setRawPayload(str(body.get("rawPayload")));
    payment.setCreatedAt(Instant.now());

    Map<String, Object> out = new LinkedHashMap<>();
    if (!"SUCCESS".equals(status)) {
      paymentRepository.save(payment);
      out.put("paymentId", payment.getId());
      out.put("invoice", invoiceDetail(invoice));
      out.put("renewed", false);
      out.put("marketplaceFulfilled", false);
      return out;
    }

    if (!"PAID".equalsIgnoreCase(invoice.getStatus())) {
      if (!"ISSUED".equalsIgnoreCase(invoice.getStatus()) && !"DRAFT".equalsIgnoreCase(invoice.getStatus())) {
        throw new IllegalArgumentException("Invoice must be DRAFT or ISSUED to mark PAID (status=" + invoice.getStatus() + ")");
      }
      Instant now = Instant.now();
      if ("DRAFT".equalsIgnoreCase(invoice.getStatus())) {
        invoice.setIssuedAt(now);
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
          invoice.setInvoiceNumber(nextInvoiceNumber(now));
        }
      }
      invoice.setStatus("PAID");
      invoice.setUpdatedAt(now);
      invoiceRepository.save(invoice);

      if (invoice.getCouponCode() != null && !invoice.getCouponCode().isBlank()) {
        couponRepository
            .findByCodeIgnoreCase(invoice.getCouponCode())
            .ifPresent(
                c -> {
                  c.setRedemptionCount(c.getRedemptionCount() + 1);
                  c.setUpdatedAt(Instant.now());
                  couponRepository.save(c);
                });
      }
    }

    boolean oneTime = "ONE_TIME".equalsIgnoreCase(invoice.getBillingCycleCode());
    int renewDays = (int) asLong(body.get("renewDays"), -1);
    boolean renewed = false;
    Map<String, Object> license = null;
    if (!oneTime) {
      if (renewDays <= 0) {
        BillingCycleEntity cycle =
            invoice.getBillingCycleCode() == null
                ? null
                : billingCycleRepository.findById(invoice.getBillingCycleCode()).orElse(null);
        renewDays = cycle == null ? 30 : Math.max(1, cycle.getMonths() * 30);
      }
      payment.setRenewDays(renewDays);
      license =
          lifecycleService.renew(
              organizationId, Map.of("days", renewDays, "notes", "Paid invoice #" + invoiceId));
      renewed = true;
    } else {
      payment.setRenewDays(0);
      renewDays = 0;
    }
    paymentRepository.save(payment);

    Map<String, Object> marketplace = addonMarketplaceService.fulfillPaidInvoice(invoice);

    out.put("paymentId", payment.getId());
    out.put("invoice", invoiceDetail(invoice));
    out.put("license", license);
    out.put("renewed", renewed);
    out.put("renewDays", renewDays);
    out.put("marketplace", marketplace);
    out.put(
        "marketplaceFulfilled",
        marketplace.get("addons") instanceof List<?> list && !list.isEmpty());
    return out;
  }

  /** Create Razorpay (or simulated) order for an ISSUED/DRAFT invoice and persist gateway_order_id. */
  @Transactional
  public Map<String, Object> createRazorpayOrder(String organizationId, long invoiceId) {
    SubscriptionInvoiceEntity invoice = requireInvoice(organizationId, invoiceId);
    if ("PAID".equalsIgnoreCase(invoice.getStatus()) || "VOID".equalsIgnoreCase(invoice.getStatus())) {
      throw new IllegalArgumentException("Cannot create payment order for status=" + invoice.getStatus());
    }
    if ("DRAFT".equalsIgnoreCase(invoice.getStatus())) {
      Instant now = Instant.now();
      invoice.setStatus("ISSUED");
      invoice.setIssuedAt(now);
      invoice.setDueAt(now.plus(7, ChronoUnit.DAYS));
      if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
        invoice.setInvoiceNumber(nextInvoiceNumber(now));
      }
      invoice.setUpdatedAt(now);
    }
    if (invoice.getTotalMinor() < 100) {
      throw new IllegalArgumentException("Invoice total must be at least ₹1 (100 paise)");
    }

    Map<String, Object> notes = new LinkedHashMap<>();
    notes.put("organizationId", organizationId);
    notes.put("invoiceId", String.valueOf(invoiceId));
    Map<String, Object> order =
        razorpayClient.createOrder(
            invoice.getTotalMinor(),
            invoice.getCurrency(),
            "inv-" + invoiceId,
            notes);
    String orderId = String.valueOf(order.get("razorpayOrderId"));
    invoice.setGatewayOrderId(orderId);
    invoice.setUpdatedAt(Instant.now());
    invoiceRepository.save(invoice);

    Map<String, Object> out = new LinkedHashMap<>(order);
    out.put("invoiceId", invoiceId);
    out.put("organizationId", organizationId);
    out.put("invoiceNumber", invoice.getInvoiceNumber());
    out.put("invoiceStatus", invoice.getStatus());
    out.put("totalMinor", invoice.getTotalMinor());
    return out;
  }

  /** Checkout success confirm: verifies signature then marks invoice PAID. */
  @Transactional
  public Map<String, Object> confirmRazorpayPayment(Map<String, Object> body) {
    String organizationId = requireStr(body.get("organizationId"), "organizationId");
    long invoiceId = asLong(body.get("invoiceId"), -1);
    if (invoiceId < 0) {
      throw new IllegalArgumentException("invoiceId is required");
    }
    String orderId = requireStr(body.get("razorpayOrderId"), "razorpayOrderId");
    String paymentId = requireStr(body.get("razorpayPaymentId"), "razorpayPaymentId");
    String signature = str(body.get("razorpaySignature"));

    SubscriptionInvoiceEntity invoice = requireInvoice(organizationId, invoiceId);
    if (invoice.getGatewayOrderId() != null
        && !invoice.getGatewayOrderId().isBlank()
        && !invoice.getGatewayOrderId().equals(orderId)) {
      throw new IllegalArgumentException("razorpayOrderId does not match invoice gateway order");
    }
    if (!razorpayClient.verifyCheckoutSignature(orderId, paymentId, signature == null ? "" : signature)) {
      throw new IllegalArgumentException("Invalid Razorpay checkout signature");
    }

    Map<String, Object> webhookBody = new LinkedHashMap<>();
    webhookBody.put("organizationId", organizationId);
    webhookBody.put("invoiceId", invoiceId);
    webhookBody.put("status", "SUCCESS");
    webhookBody.put("provider", "RAZORPAY");
    webhookBody.put("providerPaymentId", paymentId);
    webhookBody.put("gatewayOrderId", orderId);
    webhookBody.put("amountMinor", invoice.getTotalMinor());
    webhookBody.put("currency", invoice.getCurrency());
    webhookBody.put("rawPayload", str(body.get("rawPayload")));
    return applyPaymentWebhook(webhookBody);
  }

  /** Razorpay server webhook (payment.captured). Signature verified when live mode. */
  @Transactional
  public Map<String, Object> applyRazorpayWebhook(String rawBody, String signatureHeader) {
    if (!razorpayClient.verifyWebhookSignature(rawBody == null ? "" : rawBody, signatureHeader == null ? "" : signatureHeader)) {
      throw new IllegalArgumentException("Invalid Razorpay webhook signature");
    }
    Map<String, Object> payload;
    try {
      payload = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid Razorpay webhook JSON");
    }
    String event = str(payload.get("event"));
    @SuppressWarnings("unchecked")
    Map<String, Object> paymentEntity =
        nestedMap(nestedMap(payload.get("payload"), "payment"), "entity");
    if (paymentEntity == null || paymentEntity.isEmpty()) {
      Map<String, Object> ignored = new LinkedHashMap<>();
      ignored.put("ignored", true);
      ignored.put("event", event);
      ignored.put("reason", "no payment entity");
      return ignored;
    }
    String status = str(paymentEntity.get("status"));
    if (!"captured".equalsIgnoreCase(status) && !"authorized".equalsIgnoreCase(status)) {
      Map<String, Object> ignored = new LinkedHashMap<>();
      ignored.put("ignored", true);
      ignored.put("event", event);
      ignored.put("paymentStatus", status);
      return ignored;
    }

    String orderId = str(paymentEntity.get("order_id"));
    String paymentId = str(paymentEntity.get("id"));
    long amount = asLong(paymentEntity.get("amount"), -1);

    SubscriptionInvoiceEntity invoice =
        orderId == null || orderId.isBlank()
            ? null
            : invoiceRepository.findByGatewayOrderId(orderId).orElse(null);
    if (invoice == null) {
      @SuppressWarnings("unchecked")
      Map<String, Object> notes = (Map<String, Object>) paymentEntity.get("notes");
      if (notes != null) {
        String org = str(notes.get("organizationId"));
        long invId = asLong(notes.get("invoiceId"), -1);
        if (org != null && invId > 0) {
          invoice = invoiceRepository.findByIdAndOrganizationId(invId, org).orElse(null);
        }
      }
    }
    if (invoice == null) {
      throw new IllegalArgumentException("No invoice for Razorpay order " + orderId);
    }

    Map<String, Object> webhookBody = new LinkedHashMap<>();
    webhookBody.put("organizationId", invoice.getOrganizationId());
    webhookBody.put("invoiceId", invoice.getId());
    webhookBody.put("status", "SUCCESS");
    webhookBody.put("provider", "RAZORPAY");
    webhookBody.put("providerPaymentId", paymentId);
    webhookBody.put("gatewayOrderId", orderId);
    webhookBody.put("amountMinor", amount > 0 ? amount : invoice.getTotalMinor());
    webhookBody.put("currency", strOr(paymentEntity.get("currency"), invoice.getCurrency()));
    webhookBody.put("rawPayload", rawBody);
    return applyPaymentWebhook(webhookBody);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> nestedMap(Object root, String key) {
    if (!(root instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Object child = map.get(key);
    if (child instanceof Map<?, ?> nested) {
      return (Map<String, Object>) nested;
    }
    return Map.of();
  }

  public List<Map<String, Object>> listPayments(String organizationId) {
    return paymentRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(this::paymentToMap)
        .toList();
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

  private BillingCouponEntity requireValidCoupon(
      String code, String planId, long subtotalMinor, Instant now) {
    BillingCouponEntity coupon =
        couponRepository
            .findByCodeIgnoreCase(code)
            .orElseThrow(() -> new IllegalArgumentException("Unknown coupon: " + code));
    if (!coupon.isActive()) {
      throw new IllegalArgumentException("Coupon inactive: " + code);
    }
    if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
      throw new IllegalArgumentException("Coupon not yet valid: " + code);
    }
    if (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo())) {
      throw new IllegalArgumentException("Coupon expired: " + code);
    }
    if (coupon.getMaxRedemptions() != null && coupon.getRedemptionCount() >= coupon.getMaxRedemptions()) {
      throw new IllegalArgumentException("Coupon fully redeemed: " + code);
    }
    if (coupon.getApplicablePlanId() != null
        && !coupon.getApplicablePlanId().isBlank()
        && !coupon.getApplicablePlanId().equalsIgnoreCase(planId)) {
      throw new IllegalArgumentException("Coupon not applicable to plan " + planId);
    }
    if (subtotalMinor < coupon.getMinSubtotalMinor()) {
      throw new IllegalArgumentException("Subtotal below coupon minimum");
    }
    return coupon;
  }

  private long computeDiscount(BillingCouponEntity coupon, long subtotalMinor) {
    if ("FIXED_MINOR".equalsIgnoreCase(coupon.getDiscountType())) {
      return Math.min(subtotalMinor, coupon.getDiscountValue());
    }
    return BigDecimal.valueOf(subtotalMinor)
        .multiply(BigDecimal.valueOf(coupon.getDiscountValue()))
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
        .longValue();
  }

  private static long bps(long amount, int basisPoints) {
    return BigDecimal.valueOf(amount)
        .multiply(BigDecimal.valueOf(basisPoints))
        .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
        .longValue();
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

  private SubscriptionInvoiceEntity requireInvoice(String organizationId, long invoiceId) {
    return invoiceRepository
        .findByIdAndOrganizationId(invoiceId, organizationId)
        .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));
  }

  private void clearDefaultBooks() {
    for (PriceBookEntity book : priceBookRepository.findAll()) {
      if (book.isDefaultBook()) {
        book.setDefaultBook(false);
        book.setUpdatedAt(Instant.now());
        priceBookRepository.save(book);
      }
    }
  }

  private PriceBookEntity resolvePriceBook(String priceBookId) {
    if (priceBookId != null && !priceBookId.isBlank()) {
      return requirePriceBook(priceBookId);
    }
    return priceBookRepository
        .findFirstByDefaultBookTrueAndActiveTrue()
        .orElseThrow(() -> new IllegalArgumentException("No default price book configured"));
  }

  private PriceBookEntity requirePriceBook(String priceBookId) {
    return priceBookRepository
        .findById(priceBookId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown priceBookId: " + priceBookId));
  }

  private BillingCycleEntity requireCycle(String code) {
    return billingCycleRepository
        .findById(code)
        .filter(BillingCycleEntity::isActive)
        .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive billing cycle: " + code));
  }

  private String nextInvoiceNumber(Instant when) {
    String stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(when);
    return "INV-" + stamp + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
  }

  private Map<String, Object> invoiceDetail(SubscriptionInvoiceEntity invoice) {
    Map<String, Object> map = invoiceSummary(invoice);
    List<Map<String, Object>> lines = new ArrayList<>();
    for (SubscriptionInvoiceLineEntity line :
        invoiceLineRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(invoice.getId())) {
      lines.add(invoiceLineToMap(line));
    }
    map.put("lines", lines);
    return map;
  }

  private Map<String, Object> invoiceSummary(SubscriptionInvoiceEntity invoice) {
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
    map.put("couponCode", invoice.getCouponCode());
    map.put("taxRuleId", invoice.getTaxRuleId());
    map.put("cgstMinor", invoice.getCgstMinor());
    map.put("sgstMinor", invoice.getSgstMinor());
    map.put("igstMinor", invoice.getIgstMinor());
    map.put("taxMinor", invoice.getTaxMinor());
    map.put("totalMinor", invoice.getTotalMinor());
    map.put("placeOfSupply", invoice.getPlaceOfSupply());
    map.put("sellerStateCode", invoice.getSellerStateCode());
    map.put("prorationFactor", invoice.getProrationFactor());
    map.put("periodStart", invoice.getPeriodStart());
    map.put("periodEnd", invoice.getPeriodEnd());
    map.put("issuedAt", invoice.getIssuedAt());
    map.put("dueAt", invoice.getDueAt());
    map.put("notes", invoice.getNotes());
    map.put("gatewayOrderId", invoice.getGatewayOrderId());
    map.put("createdAt", invoice.getCreatedAt());
    map.put("updatedAt", invoice.getUpdatedAt());
    return map;
  }

  private Map<String, Object> invoiceLineToMap(SubscriptionInvoiceLineEntity line) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", line.getId());
    map.put("lineType", line.getLineType());
    map.put("description", line.getDescription());
    map.put("quantity", line.getQuantity());
    map.put("unitAmountMinor", line.getUnitAmountMinor());
    map.put("amountMinor", line.getAmountMinor());
    map.put("planId", line.getPlanId());
    map.put("sortOrder", line.getSortOrder());
    return map;
  }

  private Map<String, Object> cycleToMap(BillingCycleEntity entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("code", entity.getCode());
    map.put("name", entity.getName());
    map.put("months", entity.getMonths());
    map.put("sortOrder", entity.getSortOrder());
    map.put("active", entity.isActive());
    return map;
  }

  private Map<String, Object> priceBookToMap(PriceBookEntity entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", entity.getId());
    map.put("code", entity.getCode());
    map.put("name", entity.getName());
    map.put("currency", entity.getCurrency());
    map.put("active", entity.isActive());
    map.put("isDefault", entity.isDefaultBook());
    map.put("notes", entity.getNotes());
    map.put("createdAt", entity.getCreatedAt());
    map.put("updatedAt", entity.getUpdatedAt());
    return map;
  }

  private Map<String, Object> planPriceToMap(PlanPriceEntity entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", entity.getId());
    map.put("priceBookId", entity.getPriceBookId());
    map.put("planId", entity.getPlanId());
    map.put("billingCycleCode", entity.getBillingCycleCode());
    map.put("amountMinor", entity.getAmountMinor());
    map.put("currency", entity.getCurrency());
    map.put("active", entity.isActive());
    map.put("updatedAt", entity.getUpdatedAt());
    return map;
  }

  private Map<String, Object> taxRuleToMap(TaxRuleEntity entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", entity.getId());
    map.put("code", entity.getCode());
    map.put("name", entity.getName());
    map.put("countryCode", entity.getCountryCode());
    map.put("hsnSac", entity.getHsnSac());
    map.put("cgstBps", entity.getCgstBps());
    map.put("sgstBps", entity.getSgstBps());
    map.put("igstBps", entity.getIgstBps());
    map.put("active", entity.isActive());
    map.put("isDefault", entity.isDefaultRule());
    map.put("notes", entity.getNotes());
    return map;
  }

  private Map<String, Object> couponToMap(BillingCouponEntity entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", entity.getId());
    map.put("code", entity.getCode());
    map.put("name", entity.getName());
    map.put("discountType", entity.getDiscountType());
    map.put("discountValue", entity.getDiscountValue());
    map.put("currency", entity.getCurrency());
    map.put("maxRedemptions", entity.getMaxRedemptions());
    map.put("redemptionCount", entity.getRedemptionCount());
    map.put("minSubtotalMinor", entity.getMinSubtotalMinor());
    map.put("applicablePlanId", entity.getApplicablePlanId());
    map.put("active", entity.isActive());
    map.put("validFrom", entity.getValidFrom());
    map.put("validTo", entity.getValidTo());
    map.put("notes", entity.getNotes());
    return map;
  }

  private Map<String, Object> paymentToMap(SubscriptionPaymentEntity payment) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", payment.getId());
    map.put("organizationId", payment.getOrganizationId());
    map.put("invoiceId", payment.getInvoiceId());
    map.put("provider", payment.getProvider());
    map.put("providerPaymentId", payment.getProviderPaymentId());
    map.put("gatewayOrderId", payment.getGatewayOrderId());
    map.put("amountMinor", payment.getAmountMinor());
    map.put("currency", payment.getCurrency());
    map.put("status", payment.getStatus());
    map.put("renewDays", payment.getRenewDays());
    map.put("createdAt", payment.getCreatedAt());
    return map;
  }

  private static String requireStr(Object value, String field) {
    String s = str(value);
    if (s == null || s.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return s.trim();
  }

  private static String str(Object value) {
    return value == null ? null : String.valueOf(value).trim();
  }

  private static String strOr(Object value, String fallback) {
    String s = str(value);
    return s == null || s.isBlank() ? fallback : s;
  }

  private static boolean asBool(Object value, boolean fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }

  private static long asLong(Object value, long fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }
}
