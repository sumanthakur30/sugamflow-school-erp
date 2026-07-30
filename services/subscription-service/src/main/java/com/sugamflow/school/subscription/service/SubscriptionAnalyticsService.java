package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.persistence.entity.BillingCycleEntity;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Phase 10 platform analytics — MRR/ARR estimates, renewals, plan mix, revenue, usage heat.
 * Read-only over existing tables; does not change entitlements contracts.
 */
@Service
public class SubscriptionAnalyticsService {

  private static final DateTimeFormatter MONTH_KEY =
      DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

  private final TenantSubscriptionRepository tenantSubscriptionRepository;
  private final TenantSubscriptionLifecycleRepository lifecycleRepository;
  private final SubscriptionInvoiceRepository invoiceRepository;
  private final SubscriptionPaymentRepository paymentRepository;
  private final PlanPriceRepository planPriceRepository;
  private final PriceBookRepository priceBookRepository;
  private final BillingCycleRepository billingCycleRepository;
  private final UsageCounterRepository usageCounterRepository;
  private final TenantAddonRepository tenantAddonRepository;

  public SubscriptionAnalyticsService(
      TenantSubscriptionRepository tenantSubscriptionRepository,
      TenantSubscriptionLifecycleRepository lifecycleRepository,
      SubscriptionInvoiceRepository invoiceRepository,
      SubscriptionPaymentRepository paymentRepository,
      PlanPriceRepository planPriceRepository,
      PriceBookRepository priceBookRepository,
      BillingCycleRepository billingCycleRepository,
      UsageCounterRepository usageCounterRepository,
      TenantAddonRepository tenantAddonRepository) {
    this.tenantSubscriptionRepository = tenantSubscriptionRepository;
    this.lifecycleRepository = lifecycleRepository;
    this.invoiceRepository = invoiceRepository;
    this.paymentRepository = paymentRepository;
    this.planPriceRepository = planPriceRepository;
    this.priceBookRepository = priceBookRepository;
    this.billingCycleRepository = billingCycleRepository;
    this.usageCounterRepository = usageCounterRepository;
    this.tenantAddonRepository = tenantAddonRepository;
  }

  public Map<String, Object> overview() {
    Instant now = Instant.now();
    Instant day7 = now.plus(7, ChronoUnit.DAYS);
    Instant day30 = now.plus(30, ChronoUnit.DAYS);
    Instant ago30 = now.minus(30, ChronoUnit.DAYS);

    long tenants = tenantSubscriptionRepository.count();
    long active = lifecycleRepository.countByStatusIgnoreCase("ACTIVE");
    long trial = lifecycleRepository.countByStatusIgnoreCase("TRIAL");
    long grace = lifecycleRepository.countByStatusIgnoreCase("GRACE");
    long expired = lifecycleRepository.countByStatusIgnoreCase("EXPIRED");
    long suspended = lifecycleRepository.countByStatusIgnoreCase("SUSPENDED");

    Map<String, Long> mrr = estimateMrrMinor();
    long mrrMinor = mrr.getOrDefault("mrrMinor", 0L);
    long arrMinor = mrrMinor * 12;

    long paid30 = paymentRepository.sumSuccessfulAmountSince(ago30);
    long issuedOutstanding = invoiceRepository.sumIssuedOutstandingMinor();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("asOf", now.toString());
    out.put("currency", "INR");
    out.put("amountsUnit", "minor");
    out.put("tenantCount", tenants);
    out.put("lifecycle", Map.of(
        "ACTIVE", active,
        "TRIAL", trial,
        "GRACE", grace,
        "EXPIRED", expired,
        "SUSPENDED", suspended));
    out.put("mrrMinor", mrrMinor);
    out.put("arrMinor", arrMinor);
    out.put("mrrSource", mrr.get("pricedTenants") + " priced active/trial tenants on default book MONTHLY");
    out.put("pricedTenantCount", mrr.getOrDefault("pricedTenants", 0L));
    out.put("unpricedTenantCount", mrr.getOrDefault("unpricedTenants", 0L));
    out.put("collectedLast30dMinor", paid30);
    out.put("issuedOutstandingMinor", issuedOutstanding);
    out.put("invoiceCounts", invoiceCounts());
    out.put("renewalsDue7d", lifecycleRepository.countExpiringBetween(now, day7));
    out.put("renewalsDue30d", lifecycleRepository.countExpiringBetween(now, day30));
    out.put("activeAddonCount", tenantAddonRepository.count());
    out.put("paymentSuccessCount", paymentRepository.countByStatusIgnoreCase("SUCCESS"));
    out.put("paymentFailedCount", paymentRepository.countByStatusIgnoreCase("FAILED"));
    return out;
  }

  public List<Map<String, Object>> planMix() {
    Map<String, String> orgPlan = tenantSubscriptionRepository.findAll().stream()
        .collect(Collectors.toMap(
            TenantSubscriptionEntity::getOrganizationId,
            TenantSubscriptionEntity::getPlanId,
            (a, b) -> a));
    Map<String, String> orgStatus = lifecycleRepository.findAll().stream()
        .collect(Collectors.toMap(
            TenantSubscriptionLifecycleEntity::getOrganizationId,
            TenantSubscriptionLifecycleEntity::getStatus,
            (a, b) -> a));

    Map<String, long[]> buckets = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : orgPlan.entrySet()) {
      String planId = e.getValue() == null ? "unknown" : e.getValue();
      long[] row = buckets.computeIfAbsent(planId, k -> new long[3]);
      row[0]++; // tenants
      String status = orgStatus.getOrDefault(e.getKey(), "ACTIVE");
      if ("ACTIVE".equalsIgnoreCase(status) || "TRIAL".equalsIgnoreCase(status)) {
        row[1]++;
      }
      if ("GRACE".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
        row[2]++;
      }
    }

    PriceBookEntity book = defaultBookOrNull();
    Map<String, Long> monthlyByPlan = new HashMap<>();
    if (book != null) {
      for (PlanPriceEntity p :
          planPriceRepository.findByPriceBookIdOrderByPlanIdAscBillingCycleCodeAsc(book.getId())) {
        if (p.isActive() && "MONTHLY".equalsIgnoreCase(p.getBillingCycleCode())) {
          monthlyByPlan.put(p.getPlanId(), p.getAmountMinor());
        }
      }
    }

    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, long[]> e : buckets.entrySet()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("planId", e.getKey());
      row.put("tenantCount", e.getValue()[0]);
      row.put("activeOrTrialCount", e.getValue()[1]);
      row.put("atRiskCount", e.getValue()[2]);
      Long price = monthlyByPlan.get(e.getKey());
      row.put("listMonthlyMinor", price);
      row.put(
          "estimatedMrrMinor",
          price == null ? 0L : price * e.getValue()[1]);
      out.add(row);
    }
    out.sort(Comparator.comparingLong((Map<String, Object> m) -> ((Number) m.get("tenantCount")).longValue()).reversed());
    return out;
  }

  public Map<String, Object> renewals(int withinDays) {
    int days = Math.max(1, Math.min(withinDays, 365));
    Instant now = Instant.now();
    Instant to = now.plus(days, ChronoUnit.DAYS);
    List<TenantSubscriptionLifecycleEntity> rows =
        lifecycleRepository.findExpiringBetween(now, to);
    Map<String, String> plans =
        tenantSubscriptionRepository.findAll().stream()
            .collect(
                Collectors.toMap(
                    TenantSubscriptionEntity::getOrganizationId,
                    TenantSubscriptionEntity::getPlanId,
                    (a, b) -> a));

    List<Map<String, Object>> items = new ArrayList<>();
    for (TenantSubscriptionLifecycleEntity l : rows) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("organizationId", l.getOrganizationId());
      m.put("planId", plans.get(l.getOrganizationId()));
      m.put("status", l.getStatus());
      m.put("expiresAt", l.getExpiresAt());
      m.put("graceEndsAt", l.getGraceEndsAt());
      m.put("enforcementEnabled", l.isEnforcementEnabled());
      long daysLeft =
          l.getExpiresAt() == null
              ? -1
              : ChronoUnit.DAYS.between(now, l.getExpiresAt());
      m.put("daysUntilExpiry", daysLeft);
      items.add(m);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("withinDays", days);
    out.put("count", items.size());
    out.put("items", items);
    return out;
  }

  public Map<String, Object> revenue(int months) {
    int window = Math.max(1, Math.min(months, 24));
    ZonedDateTime nowZ = Instant.now().atZone(ZoneOffset.UTC);
    Instant from = nowZ.minusMonths(window).toInstant();
    List<SubscriptionPaymentEntity> payments = paymentRepository.findSuccessfulSince(from);

    Map<String, Long> byMonth = new LinkedHashMap<>();
    ZonedDateTime cursor = nowZ.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    for (int i = window - 1; i >= 0; i--) {
      byMonth.put(MONTH_KEY.format(cursor.minusMonths(i)), 0L);
    }
    for (SubscriptionPaymentEntity p : payments) {
      String key = MONTH_KEY.format(p.getCreatedAt());
      byMonth.merge(key, p.getAmountMinor(), Long::sum);
    }

    List<Map<String, Object>> series = new ArrayList<>();
    for (Map.Entry<String, Long> e : byMonth.entrySet()) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("month", e.getKey());
      point.put("collectedMinor", e.getValue());
      series.add(point);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("months", window);
    out.put("currency", "INR");
    out.put("series", series);
    out.put(
        "totalCollectedMinor",
        series.stream().mapToLong(s -> ((Number) s.get("collectedMinor")).longValue()).sum());
    return out;
  }

  public Map<String, Object> usageHeatmap(int limit) {
    int top = Math.max(5, Math.min(limit, 100));
    List<UsageCounterEntity> all = usageCounterRepository.findTopUsed();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (UsageCounterEntity u : all) {
      if (rows.size() >= top) {
        break;
      }
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("organizationId", u.getOrganizationId());
      m.put("limitCode", u.getLimitCode());
      m.put("periodKey", u.getPeriodKey());
      m.put("usedValue", u.getUsedValue());
      m.put("updatedAt", u.getUpdatedAt());
      rows.add(m);
    }

    Map<String, Long> byLimit = new LinkedHashMap<>();
    for (UsageCounterEntity u : all) {
      byLimit.merge(u.getLimitCode(), u.getUsedValue(), Long::sum);
    }
    List<Map<String, Object>> byLimitRows = new ArrayList<>();
    byLimit.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(
            e -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("limitCode", e.getKey());
              m.put("totalUsed", e.getValue());
              byLimitRows.add(m);
            });

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("top", rows);
    out.put("byLimitCode", byLimitRows);
    return out;
  }

  public Map<String, Object> dashboard() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("overview", overview());
    out.put("planMix", planMix());
    out.put("renewals", renewals(30));
    out.put("revenue", revenue(6));
    out.put("usage", usageHeatmap(25));
    return out;
  }

  private Map<String, Object> invoiceCounts() {
    Map<String, Object> counts = new LinkedHashMap<>();
    for (Object[] row : invoiceRepository.countGroupedByStatus()) {
      counts.put(String.valueOf(row[0]).toUpperCase(Locale.ROOT), ((Number) row[1]).longValue());
    }
    return counts;
  }

  /**
   * MRR ≈ sum(MONTHLY list price) for tenants in ACTIVE/TRIAL on the default price book. Unpriced
   * tenants counted separately (not invented).
   */
  private Map<String, Long> estimateMrrMinor() {
    PriceBookEntity book = defaultBookOrNull();
    Map<String, Long> monthly = new HashMap<>();
    if (book != null) {
      for (PlanPriceEntity p :
          planPriceRepository.findByPriceBookIdOrderByPlanIdAscBillingCycleCodeAsc(book.getId())) {
        if (p.isActive() && "MONTHLY".equalsIgnoreCase(p.getBillingCycleCode())) {
          monthly.put(p.getPlanId(), p.getAmountMinor());
        }
      }
    }

    Map<String, String> statusByOrg =
        lifecycleRepository.findAll().stream()
            .collect(
                Collectors.toMap(
                    TenantSubscriptionLifecycleEntity::getOrganizationId,
                    TenantSubscriptionLifecycleEntity::getStatus,
                    (a, b) -> a));

    long mrr = 0;
    long priced = 0;
    long unpriced = 0;
    for (TenantSubscriptionEntity t : tenantSubscriptionRepository.findAll()) {
      String status = statusByOrg.getOrDefault(t.getOrganizationId(), "ACTIVE");
      if (!"ACTIVE".equalsIgnoreCase(status) && !"TRIAL".equalsIgnoreCase(status)) {
        continue;
      }
      Long price = monthly.get(t.getPlanId());
      if (price == null) {
        unpriced++;
        continue;
      }
      mrr += price;
      priced++;
    }
    Map<String, Long> out = new LinkedHashMap<>();
    out.put("mrrMinor", mrr);
    out.put("pricedTenants", priced);
    out.put("unpricedTenants", unpriced);
    return out;
  }

  private PriceBookEntity defaultBookOrNull() {
    return priceBookRepository.findFirstByDefaultBookTrueAndActiveTrue().orElse(null);
  }

  /** Normalize an invoice/payment amount to monthly minor units using billing cycle months. */
  @SuppressWarnings("unused")
  private long toMonthlyMinor(long amountMinor, String billingCycleCode) {
    if (billingCycleCode == null || "ONE_TIME".equalsIgnoreCase(billingCycleCode)) {
      return 0;
    }
    int months =
        billingCycleRepository
            .findById(billingCycleCode)
            .map(BillingCycleEntity::getMonths)
            .orElse(1);
    if (months <= 0) {
      return 0;
    }
    return BigDecimal.valueOf(amountMinor)
        .divide(BigDecimal.valueOf(months), 0, RoundingMode.HALF_UP)
        .longValue();
  }
}
