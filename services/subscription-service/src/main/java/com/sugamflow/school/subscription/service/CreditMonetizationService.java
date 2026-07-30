package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.persistence.entity.CreditLedgerEntity;
import com.sugamflow.school.subscription.persistence.entity.CreditPeriodRunEntity;
import com.sugamflow.school.subscription.persistence.entity.CreditPolicyEntity;
import com.sugamflow.school.subscription.persistence.entity.CreditWalletEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.repo.CreditLedgerRepository;
import com.sugamflow.school.subscription.persistence.repo.CreditPeriodRunRepository;
import com.sugamflow.school.subscription.persistence.repo.CreditPolicyRepository;
import com.sugamflow.school.subscription.persistence.repo.CreditWalletRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 13 AI / usage credit monetization: policies, consume/grant, period carry-forward + expiry.
 * Additive over Phase 9 wallets; entitlements JSON contracts unchanged.
 */
@Service
public class CreditMonetizationService {

  private static final Set<String> PERIOD_TYPES = Set.of("NONE", "MONTHLY", "QUARTERLY", "YEARLY");

  private final CreditPolicyRepository policyRepository;
  private final CreditPeriodRunRepository periodRunRepository;
  private final CreditWalletRepository walletRepository;
  private final CreditLedgerRepository ledgerRepository;
  private final TenantSubscriptionRepository tenantSubscriptionRepository;

  public CreditMonetizationService(
      CreditPolicyRepository policyRepository,
      CreditPeriodRunRepository periodRunRepository,
      CreditWalletRepository walletRepository,
      CreditLedgerRepository ledgerRepository,
      TenantSubscriptionRepository tenantSubscriptionRepository) {
    this.policyRepository = policyRepository;
    this.periodRunRepository = periodRunRepository;
    this.walletRepository = walletRepository;
    this.ledgerRepository = ledgerRepository;
    this.tenantSubscriptionRepository = tenantSubscriptionRepository;
  }

  public List<Map<String, Object>> listPolicies() {
    return policyRepository.findAll().stream().map(this::policyToMap).toList();
  }

  @Transactional
  public Map<String, Object> upsertPolicy(Map<String, Object> body) {
    String meter = requireStr(body.get("meterCode"), "meterCode").toLowerCase(Locale.ROOT);
    CreditPolicyEntity entity =
        policyRepository.findById(meter).orElseGet(CreditPolicyEntity::new);
    if (entity.getMeterCode() == null) {
      entity.setMeterCode(meter);
    }
    entity.setName(requireStr(body.get("name"), "name"));
    String period = strOr(body.get("periodType"), entity.getPeriodType()).toUpperCase(Locale.ROOT);
    if (!PERIOD_TYPES.contains(period)) {
      throw new IllegalArgumentException("Invalid periodType: " + period);
    }
    entity.setPeriodType(period);
    int bps = (int) asLong(body.get("carryForwardBps"), entity.getCarryForwardBps());
    if (bps < 0 || bps > 10_000) {
      throw new IllegalArgumentException("carryForwardBps must be 0..10000");
    }
    entity.setCarryForwardBps(bps);
    if (body.containsKey("carryForwardCap")) {
      Object cap = body.get("carryForwardCap");
      entity.setCarryForwardCap(
          cap == null || String.valueOf(cap).isBlank() ? null : asLong(cap, 0));
    }
    if (body.containsKey("expireUnused")) {
      entity.setExpireUnused(asBool(body.get("expireUnused"), true));
    }
    entity.setPlanGrantAmount(asLong(body.get("planGrantAmount"), entity.getPlanGrantAmount()));
    if (body.containsKey("active")) {
      entity.setActive(asBool(body.get("active"), true));
    }
    if (body.containsKey("notes")) {
      entity.setNotes(str(body.get("notes")));
    }
    entity.setUpdatedAt(Instant.now());
    return policyToMap(policyRepository.save(entity));
  }

  public List<Map<String, Object>> listWallets(String organizationId) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (CreditWalletEntity w :
        walletRepository.findByOrganizationIdOrderByMeterCodeAsc(organizationId)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("organizationId", w.getOrganizationId());
      row.put("meterCode", w.getMeterCode());
      row.put("balance", w.getBalance());
      row.put("updatedAt", w.getUpdatedAt());
      policyRepository.findById(w.getMeterCode()).ifPresent(p -> row.put("policy", policyToMap(p)));
      out.add(row);
    }
    return out;
  }

  public List<Map<String, Object>> listLedger(String organizationId, String meterCode, int limit) {
    int top = Math.max(1, Math.min(limit, 200));
    List<CreditLedgerEntity> rows =
        meterCode == null || meterCode.isBlank()
            ? ledgerRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
            : ledgerRepository.findByOrganizationIdAndMeterCodeOrderByCreatedAtDesc(
                organizationId, meterCode);
    return rows.stream().limit(top).map(this::ledgerToMap).toList();
  }

  public List<Map<String, Object>> listPeriodRuns(String organizationId) {
    return periodRunRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(this::periodRunToMap)
        .toList();
  }

  /** Consume credits (AI inference, WA send, etc.). Fails if insufficient balance. */
  @Transactional
  public Map<String, Object> consume(String organizationId, Map<String, Object> body) {
    String meter = requireStr(body.get("meterCode"), "meterCode");
    long amount = asLong(body.get("amount"), -1);
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    String reason = strOr(body.get("reason"), "CONSUME");
    CreditWalletEntity wallet = requireWallet(organizationId, meter);
    if (wallet.getBalance() < amount) {
      throw new IllegalArgumentException(
          "Insufficient " + meter + " balance: have " + wallet.getBalance() + ", need " + amount);
    }
    return applyDelta(wallet, -amount, null, "CONSUME:" + reason);
  }

  /** Manual / plan grant (promo, allotment). */
  @Transactional
  public Map<String, Object> grant(String organizationId, Map<String, Object> body) {
    String meter = requireStr(body.get("meterCode"), "meterCode");
    long amount = asLong(body.get("amount"), -1);
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be > 0");
    }
    String reason = strOr(body.get("reason"), "GRANT");
    CreditWalletEntity wallet = getOrCreateWallet(organizationId, meter);
    return applyDelta(wallet, amount, null, "GRANT:" + reason);
  }

  /**
   * Close a period for one org+meter: apply carry-forward %, cap, expire remainder, then plan grant.
   * Idempotent per (org, meter, periodKey).
   */
  @Transactional
  public Map<String, Object> runCarryForward(String organizationId, Map<String, Object> body) {
    String meter = requireStr(body.get("meterCode"), "meterCode");
    CreditPolicyEntity policy =
        policyRepository
            .findById(meter)
            .filter(CreditPolicyEntity::isActive)
            .orElseThrow(() -> new IllegalArgumentException("No active credit policy for " + meter));
    if ("NONE".equalsIgnoreCase(policy.getPeriodType())) {
      throw new IllegalArgumentException("Policy periodType=NONE — carry-forward disabled");
    }

    String periodKey =
        strOr(body.get("periodKey"), defaultPeriodKey(policy.getPeriodType()));
    var existing =
        periodRunRepository.findByOrganizationIdAndMeterCodeAndPeriodKey(
            organizationId, meter, periodKey);
    if (existing.isPresent()) {
      Map<String, Object> out = periodRunToMap(existing.get());
      out.put("idempotent", true);
      return out;
    }

    CreditWalletEntity wallet = getOrCreateWallet(organizationId, meter);
    long opening = wallet.getBalance();
    long carryCandidate =
        BigDecimal.valueOf(opening)
            .multiply(BigDecimal.valueOf(policy.getCarryForwardBps()))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
            .longValue();
    long carried = carryCandidate;
    if (policy.getCarryForwardCap() != null) {
      carried = Math.min(carried, Math.max(0, policy.getCarryForwardCap()));
    }
    long expired = 0;
    if (policy.isExpireUnused()) {
      expired = Math.max(0, opening - carried);
    } else {
      // Keep full balance; "carried" is informational portion under policy pct/cap.
      carried = opening;
      expired = 0;
    }

    if (expired > 0) {
      applyDelta(wallet, -expired, null, "EXPIRE:period=" + periodKey);
    }
    // After expiry, wallet is at `carried` (if expire) or unchanged (if not).
    wallet = requireWallet(organizationId, meter);

    long granted = policy.getPlanGrantAmount();
    if (body.containsKey("planGrantAmount")) {
      granted = Math.max(0, asLong(body.get("planGrantAmount"), granted));
    }
    // Optional: only grant if tenant has a plan assignment
    if (granted > 0 && tenantSubscriptionRepository.findById(organizationId).isEmpty()) {
      granted = 0;
    }
    if (granted > 0) {
      applyDelta(wallet, granted, null, "GRANT:period=" + periodKey);
      wallet = requireWallet(organizationId, meter);
    }

    CreditPeriodRunEntity run = new CreditPeriodRunEntity();
    run.setOrganizationId(organizationId);
    run.setMeterCode(meter);
    run.setPeriodKey(periodKey);
    run.setOpeningBalance(opening);
    run.setCarriedForward(carried);
    run.setExpired(expired);
    run.setGranted(granted);
    run.setClosingBalance(wallet.getBalance());
    run.setCreatedAt(Instant.now());
    periodRunRepository.save(run);

    Map<String, Object> out = periodRunToMap(run);
    out.put("idempotent", false);
    out.put("policy", policyToMap(policy));
    return out;
  }

  /** Run carry-forward for all tenants that have a wallet for the meter. */
  @Transactional
  public Map<String, Object> runCarryForwardBatch(Map<String, Object> body) {
    String meter = requireStr(body.get("meterCode"), "meterCode");
    String periodKey = str(body.get("periodKey"));
    int processed = 0;
    int skipped = 0;
    List<Map<String, Object>> results = new ArrayList<>();
    for (CreditWalletEntity w : walletRepository.findAll()) {
      if (!meter.equals(w.getMeterCode())) {
        continue;
      }
      Map<String, Object> req = new LinkedHashMap<>();
      req.put("meterCode", meter);
      if (periodKey != null && !periodKey.isBlank()) {
        req.put("periodKey", periodKey);
      }
      if (body.containsKey("planGrantAmount")) {
        req.put("planGrantAmount", body.get("planGrantAmount"));
      }
      Map<String, Object> run = runCarryForward(w.getOrganizationId(), req);
      if (Boolean.TRUE.equals(run.get("idempotent"))) {
        skipped++;
      } else {
        processed++;
      }
      results.add(run);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("meterCode", meter);
    out.put("processed", processed);
    out.put("skippedIdempotent", skipped);
    out.put("runs", results);
    return out;
  }

  public Map<String, Object> previewCarryForward(String organizationId, String meterCode) {
    CreditPolicyEntity policy =
        policyRepository
            .findById(meterCode)
            .orElseThrow(() -> new IllegalArgumentException("Unknown policy: " + meterCode));
    long opening =
        walletRepository
            .findById(new CreditWalletEntity.Pk(organizationId, meterCode))
            .map(CreditWalletEntity::getBalance)
            .orElse(0L);
    long carryCandidate =
        BigDecimal.valueOf(opening)
            .multiply(BigDecimal.valueOf(policy.getCarryForwardBps()))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
            .longValue();
    long carried = carryCandidate;
    if (policy.getCarryForwardCap() != null) {
      carried = Math.min(carried, Math.max(0, policy.getCarryForwardCap()));
    }
    long expired = policy.isExpireUnused() ? Math.max(0, opening - carried) : 0;
    if (!policy.isExpireUnused()) {
      carried = opening;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("meterCode", meterCode);
    out.put("openingBalance", opening);
    out.put("carriedForward", carried);
    out.put("expired", expired);
    out.put("planGrantAmount", policy.getPlanGrantAmount());
    out.put("projectedClosing", carried + policy.getPlanGrantAmount());
    out.put("periodKey", defaultPeriodKey(policy.getPeriodType()));
    out.put("policy", policyToMap(policy));
    return out;
  }

  private Map<String, Object> applyDelta(
      CreditWalletEntity wallet, long delta, Long invoiceId, String reason) {
    long balance = wallet.getBalance() + delta;
    if (balance < 0) {
      throw new IllegalArgumentException("Balance cannot go negative");
    }
    wallet.setBalance(balance);
    wallet.setUpdatedAt(Instant.now());
    walletRepository.save(wallet);

    CreditLedgerEntity ledger = new CreditLedgerEntity();
    ledger.setOrganizationId(wallet.getOrganizationId());
    ledger.setMeterCode(wallet.getMeterCode());
    ledger.setDelta(delta);
    ledger.setBalanceAfter(balance);
    ledger.setReason(reason);
    ledger.setInvoiceId(invoiceId);
    ledger.setCreatedAt(Instant.now());
    ledgerRepository.save(ledger);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", wallet.getOrganizationId());
    out.put("meterCode", wallet.getMeterCode());
    out.put("delta", delta);
    out.put("balanceAfter", balance);
    out.put("reason", reason);
    return out;
  }

  private CreditWalletEntity requireWallet(String organizationId, String meterCode) {
    return walletRepository
        .findById(new CreditWalletEntity.Pk(organizationId, meterCode))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No wallet for org=" + organizationId + " meter=" + meterCode));
  }

  private CreditWalletEntity getOrCreateWallet(String organizationId, String meterCode) {
    return walletRepository
        .findById(new CreditWalletEntity.Pk(organizationId, meterCode))
        .orElseGet(
            () -> {
              CreditWalletEntity w = new CreditWalletEntity();
              w.setOrganizationId(organizationId);
              w.setMeterCode(meterCode);
              w.setBalance(0);
              w.setUpdatedAt(Instant.now());
              return walletRepository.save(w);
            });
  }

  static String defaultPeriodKey(String periodType) {
    YearMonth ym = YearMonth.now(ZoneOffset.UTC);
    return switch (periodType == null ? "MONTHLY" : periodType.toUpperCase(Locale.ROOT)) {
      case "QUARTERLY" -> ym.getYear() + "-Q" + ((ym.getMonthValue() - 1) / 3 + 1);
      case "YEARLY" -> String.valueOf(ym.getYear());
      case "NONE" -> "NONE";
      default -> ym.toString(); // YYYY-MM
    };
  }

  private Map<String, Object> policyToMap(CreditPolicyEntity p) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("meterCode", p.getMeterCode());
    map.put("name", p.getName());
    map.put("periodType", p.getPeriodType());
    map.put("carryForwardBps", p.getCarryForwardBps());
    map.put("carryForwardPct", p.getCarryForwardBps() / 100.0);
    map.put("carryForwardCap", p.getCarryForwardCap());
    map.put("expireUnused", p.isExpireUnused());
    map.put("planGrantAmount", p.getPlanGrantAmount());
    map.put("active", p.isActive());
    map.put("notes", p.getNotes());
    map.put("updatedAt", p.getUpdatedAt());
    return map;
  }

  private Map<String, Object> periodRunToMap(CreditPeriodRunEntity r) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", r.getId());
    map.put("organizationId", r.getOrganizationId());
    map.put("meterCode", r.getMeterCode());
    map.put("periodKey", r.getPeriodKey());
    map.put("openingBalance", r.getOpeningBalance());
    map.put("carriedForward", r.getCarriedForward());
    map.put("expired", r.getExpired());
    map.put("granted", r.getGranted());
    map.put("closingBalance", r.getClosingBalance());
    map.put("createdAt", r.getCreatedAt());
    return map;
  }

  private Map<String, Object> ledgerToMap(CreditLedgerEntity l) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", l.getId());
    map.put("organizationId", l.getOrganizationId());
    map.put("meterCode", l.getMeterCode());
    map.put("delta", l.getDelta());
    map.put("balanceAfter", l.getBalanceAfter());
    map.put("reason", l.getReason());
    map.put("invoiceId", l.getInvoiceId());
    map.put("createdAt", l.getCreatedAt());
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

  private static boolean asBool(Object v, boolean fallback) {
    if (v == null) {
      return fallback;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(String.valueOf(v));
  }
}
