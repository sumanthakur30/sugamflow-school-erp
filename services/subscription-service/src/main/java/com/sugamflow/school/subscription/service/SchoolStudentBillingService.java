package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.billing.BillableStudentCounter;
import com.sugamflow.school.subscription.billing.PerStudentInvoiceCommand;
import com.sugamflow.school.subscription.billing.PerStudentInvoiceIssuer;
import com.sugamflow.school.subscription.billing.SchoolPackageRate;
import com.sugamflow.school.subscription.billing.SchoolPackageRateSource;
import com.sugamflow.school.subscription.billing.SchoolPerStudentBillingMath;
import com.sugamflow.school.subscription.billing.SchoolPerStudentBillingMath.Charge;
import com.sugamflow.school.subscription.billing.SchoolPerStudentBillingMath.TaxSplit;
import com.sugamflow.school.subscription.billing.TaxRuleView;
import com.sugamflow.school.subscription.persistence.entity.SchoolBillingContractEntity;
import com.sugamflow.school.subscription.persistence.entity.SchoolBillingRunEntity;
import com.sugamflow.school.subscription.persistence.repo.SchoolBillingContractRepository;
import com.sugamflow.school.subscription.persistence.repo.SchoolBillingRunRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPaymentRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly platform invoice for a school, priced from an active-student snapshot. School fee
 * collections are not an input.
 */
@Service
public class SchoolStudentBillingService {

  public static final String OBLIGATION = "SCHOOL_PER_STUDENT";
  public static final String POLICY = "ACTIVE_ON_RUN";
  public static final String ISSUED = "ISSUED";
  public static final String FAILED = "FAILED";
  private static final Set<String> ELIGIBLE = Set.of("ACTIVE", "TRIAL", "GRACE");
  private static final String SELLER_STATE_DEFAULT = "09";

  private final SchoolBillingContractRepository contracts;
  private final SchoolBillingRunRepository runs;
  private final SubscriptionPaymentRepository payments;
  private final BillableStudentCounter students;
  private final SchoolPackageRateSource packageRates;
  private final PerStudentInvoiceIssuer invoices;
  private final SubscriptionLifecycleService lifecycle;

  public SchoolStudentBillingService(
      SchoolBillingContractRepository contracts,
      SchoolBillingRunRepository runs,
      SubscriptionPaymentRepository payments,
      BillableStudentCounter students,
      SchoolPackageRateSource packageRates,
      PerStudentInvoiceIssuer invoices,
      SubscriptionLifecycleService lifecycle) {
    this.contracts = contracts;
    this.runs = runs;
    this.payments = payments;
    this.students = students;
    this.packageRates = packageRates;
    this.invoices = invoices;
    this.lifecycle = lifecycle;
  }

  public List<String> activeOrganizationIds() {
    return contracts.findByActiveTrueAndUnitModel("PER_STUDENT").stream()
        .map(SchoolBillingContractEntity::getOrganizationId)
        .toList();
  }

  @Transactional
  public Map<String, Object> billOrganization(String organizationId, YearMonth period) {
    String periodKey = period.toString();
    Optional<SchoolBillingRunEntity> existing =
        runs.findByOrganizationIdAndPeriodKeyAndObligationType(organizationId, periodKey, OBLIGATION);
    if (existing.isPresent() && ISSUED.equals(existing.get().getJobStatus())) {
      return runToMap(existing.get());
    }
    if (existing.isPresent() && existing.get().getInvoiceId() != null) {
      SchoolBillingRunEntity locked = existing.get();
      locked.setJobStatus(ISSUED);
      locked.setErrorMessage(null);
      locked.setUpdatedAt(Instant.now());
      return runToMap(runs.save(locked));
    }

    SchoolBillingContractEntity contract =
        contracts
            .findById(organizationId)
            .filter(SchoolBillingContractEntity::isActive)
            .orElseThrow(
                () -> new IllegalArgumentException("No active per-student contract for " + organizationId));
    Map<String, Object> license = lifecycle.getLicense(organizationId);
    String status = String.valueOf(license.get("resolvedStatus"));
    if (!ELIGIBLE.contains(status)) {
      Map<String, Object> skipped = new LinkedHashMap<>();
      skipped.put("organizationId", organizationId);
      skipped.put("periodKey", periodKey);
      skipped.put("jobStatus", "SKIPPED");
      skipped.put("reason", "Subscription status " + status + " is not billable");
      return skipped;
    }

    try {
      int count = students.countActive(organizationId);
      ResolvedPrice price = resolvePrice(contract);
      Charge charge =
          SchoolPerStudentBillingMath.charge(
              count, price.unitMinor(), contract.getBaseFeeMinor(), contract.getDiscountMinor());
      TaxRuleView rule = invoices.defaultTaxRule();
      String seller = blankTo(contract.getSellerStateCode(), SELLER_STATE_DEFAULT);
      String buyer = blankTo(contract.getBuyerStateCode(), seller);
      boolean interState = !seller.equalsIgnoreCase(buyer);
      TaxSplit tax =
          SchoolPerStudentBillingMath.tax(
              charge.preTaxMinor(),
              rule.cgstBps(),
              rule.sgstBps(),
              rule.igstBps(),
              interState,
              price.gstInclusive());
      Instant start = period.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant end = period.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      Map<String, Object> invoice =
          invoices.issuePerStudent(
              new PerStudentInvoiceCommand(
                  organizationId,
                  periodKey,
                  count,
                  price.unitMinor(),
                  contract.getBaseFeeMinor(),
                  charge.discountMinor(),
                  tax.taxableMinor(),
                  tax.cgstMinor(),
                  tax.sgstMinor(),
                  tax.igstMinor(),
                  tax.taxMinor(),
                  tax.totalMinor(),
                  rule.id(),
                  seller,
                  buyer,
                  start,
                  end));
      SchoolBillingRunEntity run = existing.orElseGet(SchoolBillingRunEntity::new);
      if (run.getCreatedAt() == null) {
        run.setCreatedAt(Instant.now());
      }
      Instant now = Instant.now();
      run.setOrganizationId(organizationId);
      run.setPeriodKey(periodKey);
      run.setObligationType(OBLIGATION);
      run.setCountPolicy(POLICY);
      run.setBillableCount(count);
      run.setSnapshotAt(now);
      run.setRateVersionId(price.versionId());
      run.setUnitAmountMinor(price.unitMinor());
      run.setBaseFeeMinor(contract.getBaseFeeMinor());
      run.setDiscountMinor(charge.discountMinor());
      run.setTaxableMinor(tax.taxableMinor());
      run.setCgstMinor(tax.cgstMinor());
      run.setSgstMinor(tax.sgstMinor());
      run.setIgstMinor(tax.igstMinor());
      run.setTaxMinor(tax.taxMinor());
      run.setTotalMinor(tax.totalMinor());
      run.setInvoiceId(((Number) invoice.get("id")).longValue());
      run.setJobStatus(ISSUED);
      run.setErrorMessage(null);
      run.setUpdatedAt(now);
      return runToMap(runs.save(run));
    } catch (RuntimeException ex) {
      SchoolBillingRunEntity run = existing.orElseGet(SchoolBillingRunEntity::new);
      Instant now = Instant.now();
      if (run.getId() == null) {
        run.setOrganizationId(organizationId);
        run.setPeriodKey(periodKey);
        run.setObligationType(OBLIGATION);
        run.setCountPolicy(POLICY);
        run.setBillableCount(0);
        run.setSnapshotAt(now);
        run.setUnitAmountMinor(0);
        run.setBaseFeeMinor(0);
        run.setDiscountMinor(0);
        run.setTaxableMinor(0);
        run.setCreatedAt(now);
      }
      run.setJobStatus(FAILED);
      String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      run.setErrorMessage(message.length() > 512 ? message.substring(0, 512) : message);
      run.setUpdatedAt(now);
      runs.save(run);
      return runToMap(run);
    }
  }

  @Transactional
  public Map<String, Object> saveContract(Map<String, Object> body) {
    String organizationId = required(body.get("organizationId"), "organizationId");
    SchoolBillingContractEntity entity =
        contracts.findById(organizationId).orElseGet(SchoolBillingContractEntity::new);
    Instant now = Instant.now();
    if (entity.getOrganizationId() == null) {
      entity.setOrganizationId(organizationId);
      entity.setCreatedAt(now);
      entity.setEffectiveFrom(now);
    }
    entity.setActive(body.get("active") == null || Boolean.parseBoolean(String.valueOf(body.get("active"))));
    entity.setUnitModel("PER_STUDENT");
    entity.setCountPolicy(POLICY);
    if (body.containsKey("perStudentRateMinor")) {
      Object rate = body.get("perStudentRateMinor");
      entity.setPerStudentRateMinor(rate == null || String.valueOf(rate).isBlank() ? null : asLong(rate));
    }
    if (body.containsKey("baseFeeMinor")) {
      entity.setBaseFeeMinor(asLong(body.get("baseFeeMinor")));
    }
    if (body.containsKey("discountMinor")) {
      entity.setDiscountMinor(asLong(body.get("discountMinor")));
    }
    if (body.containsKey("buyerStateCode")) {
      entity.setBuyerStateCode(blankToNull(body.get("buyerStateCode")));
    }
    if (body.containsKey("sellerStateCode")) {
      entity.setSellerStateCode(blankToNull(body.get("sellerStateCode")));
    }
    entity.setUpdatedAt(now);
    return contractToMap(contracts.save(entity));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary(YearMonth period) {
    String periodKey = period.toString();
    List<SchoolBillingRunEntity> rows =
        runs.findByPeriodKeyAndObligationTypeOrderByOrganizationIdAsc(periodKey, OBLIGATION);
    long invoicedBase = 0;
    long collected = 0;
    long outstanding = 0;
    int invoiceCount = 0;
    int failed = 0;
    int students = 0;
    for (SchoolBillingRunEntity run : rows) {
      if (FAILED.equals(run.getJobStatus())) {
        failed++;
      }
      if (!ISSUED.equals(run.getJobStatus())) {
        continue;
      }
      invoiceCount++;
      students += run.getBillableCount();
      invoicedBase += run.getTaxableMinor();
      long paid = run.getInvoiceId() == null ? 0 : payments.sumSuccessfulForInvoice(run.getInvoiceId());
      collected += paid;
      outstanding += Math.max(0, run.getTotalMinor() - paid);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("periodKey", periodKey);
    out.put("countPolicy", POLICY);
    out.put("schoolsOnPerStudentBilling", contracts.countByActiveTrueAndUnitModel("PER_STUDENT"));
    out.put("billableStudentCount", students);
    out.put("invoicedBaseMinor", invoicedBase);
    out.put("invoiceCount", invoiceCount);
    out.put("collectedMinor", collected);
    out.put("outstandingMinor", outstanding);
    out.put("failedJobs", failed);
    out.put(
        "note",
        "Billable student count is the stored snapshot for this billing period, not the school's live enrollment. Collected includes successful payments only.");
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> runsForPeriod(YearMonth period) {
    return runs
        .findByPeriodKeyAndObligationTypeOrderByOrganizationIdAsc(period.toString(), OBLIGATION)
        .stream()
        .map(this::runToMap)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> profile(String organizationId) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put(
        "contract",
        contracts.findById(organizationId).map(this::contractToMap).orElse(null));
    List<SchoolBillingRunEntity> history =
        runs.findByOrganizationIdAndObligationTypeOrderByPeriodKeyDesc(organizationId, OBLIGATION);
    out.put("runs", history.stream().map(this::runToMap).toList());
    out.put("latest", history.stream().filter(r -> ISSUED.equals(r.getJobStatus())).findFirst().map(this::runToMap).orElse(null));
    return out;
  }

  private ResolvedPrice resolvePrice(SchoolBillingContractEntity contract) {
    Optional<SchoolPackageRate> open = packageRates.openSchoolRate();
    Long versionId = open.map(SchoolPackageRate::id).orElse(null);
    boolean inclusive = open.map(SchoolPackageRate::gstInclusive).orElse(false);
    if (contract.getPerStudentRateMinor() != null) {
      return new ResolvedPrice(contract.getPerStudentRateMinor(), versionId, inclusive);
    }
    SchoolPackageRate rate =
        open.orElseThrow(
            () -> new IllegalStateException("No open SCHOOL PER_STUDENT package rate and no contracted rate"));
    return new ResolvedPrice(rate.monthlyAmountMinor(), rate.id(), rate.gstInclusive());
  }

  private Map<String, Object> runToMap(SchoolBillingRunEntity run) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", run.getId());
    map.put("organizationId", run.getOrganizationId());
    map.put("periodKey", run.getPeriodKey());
    map.put("obligationType", run.getObligationType());
    map.put("countPolicy", run.getCountPolicy());
    map.put("billableCount", run.getBillableCount());
    map.put("snapshotAt", run.getSnapshotAt());
    map.put("rateVersionId", run.getRateVersionId());
    map.put("unitAmountMinor", run.getUnitAmountMinor());
    map.put("baseFeeMinor", run.getBaseFeeMinor());
    map.put("discountMinor", run.getDiscountMinor());
    map.put("taxableMinor", run.getTaxableMinor());
    map.put("cgstMinor", run.getCgstMinor());
    map.put("sgstMinor", run.getSgstMinor());
    map.put("igstMinor", run.getIgstMinor());
    map.put("taxMinor", run.getTaxMinor());
    map.put("totalMinor", run.getTotalMinor());
    map.put("invoiceId", run.getInvoiceId());
    map.put("jobStatus", run.getJobStatus());
    map.put("errorMessage", run.getErrorMessage());
    if (run.getInvoiceId() != null) {
      map.put("collectedMinor", payments.sumSuccessfulForInvoice(run.getInvoiceId()));
    } else {
      map.put("collectedMinor", 0L);
    }
    return map;
  }

  private Map<String, Object> contractToMap(SchoolBillingContractEntity entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("organizationId", entity.getOrganizationId());
    map.put("active", entity.isActive());
    map.put("unitModel", entity.getUnitModel());
    map.put("perStudentRateMinor", entity.getPerStudentRateMinor());
    map.put("baseFeeMinor", entity.getBaseFeeMinor());
    map.put("discountMinor", entity.getDiscountMinor());
    map.put("buyerStateCode", entity.getBuyerStateCode());
    map.put("sellerStateCode", entity.getSellerStateCode());
    map.put("countPolicy", entity.getCountPolicy());
    return map;
  }

  private static String required(Object value, String name) {
    if (value == null || String.valueOf(value).isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return String.valueOf(value).trim();
  }

  private static long asLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value).trim());
  }

  private static String blankToNull(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static String blankTo(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  public static YearMonth periodOrCurrent(String period, ZoneId zone) {
    if (period == null || period.isBlank()) {
      return YearMonth.now(zone);
    }
    return YearMonth.parse(period.trim());
  }

  private record ResolvedPrice(long unitMinor, Long versionId, boolean gstInclusive) {}
}
