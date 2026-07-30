package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.persistence.entity.LimitDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.UsageCounterEntity;
import com.sugamflow.school.subscription.persistence.entity.UsageEventEntity;
import com.sugamflow.school.subscription.persistence.repo.LimitDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.UsageCounterRepository;
import com.sugamflow.school.subscription.persistence.repo.UsageEventRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Optional usage metering. Does not change School entitlements/feature-flag contracts. Callers opt
 * in via validate / increment / decrement APIs.
 */
@Service
public class UsageMeteringService {

  public static final String PERIOD_ALL = "ALL";

  private final UsageCounterRepository usageCounterRepository;
  private final UsageEventRepository usageEventRepository;
  private final LimitDefinitionRepository limitDefinitionRepository;
  private final SubscriptionService subscriptionService;

  public UsageMeteringService(
      UsageCounterRepository usageCounterRepository,
      UsageEventRepository usageEventRepository,
      LimitDefinitionRepository limitDefinitionRepository,
      SubscriptionService subscriptionService) {
    this.usageCounterRepository = usageCounterRepository;
    this.usageEventRepository = usageEventRepository;
    this.limitDefinitionRepository = limitDefinitionRepository;
    this.subscriptionService = subscriptionService;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> listUsage(String organizationId) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    List<Map<String, Object>> counters = new ArrayList<>();
    for (UsageCounterEntity row :
        usageCounterRepository.findByOrganizationIdOrderByLimitCodeAscPeriodKeyAsc(organizationId)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("limitCode", row.getLimitCode());
      m.put("periodKey", row.getPeriodKey());
      m.put("used", row.getUsedValue());
      m.put("updatedAt", row.getUpdatedAt());
      counters.add(m);
    }
    out.put("counters", counters);
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> listLimitsWithUsage(String organizationId) {
    Map<String, Object> entitlements = subscriptionService.entitlements(organizationId);
    @SuppressWarnings("unchecked")
    Map<String, Long> limits = (Map<String, Long>) entitlements.get("limits");
    if (limits == null) {
      limits = Map.of();
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map.Entry<String, Long> e : limits.entrySet()) {
      rows.add(limitSnapshot(organizationId, e.getKey(), e.getValue(), 0L));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("planId", entitlements.get("planId"));
    out.put("limits", rows);
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> validateLimit(String organizationId, Map<String, Object> body) {
    String limitCode = requireCode(body, "limitCode");
    long delta = parseLong(body.get("delta"), 1L);
    if (delta < 0) {
      throw new IllegalArgumentException("delta must be >= 0 for validate-limit");
    }
    LimitSnapshot snap = snapshot(organizationId, limitCode, delta);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("limitCode", limitCode);
    out.put("delta", delta);
    out.put("planId", snap.planId);
    out.put("limit", snap.limit);
    out.put("used", snap.used);
    out.put("remaining", snap.remaining);
    out.put("periodKey", snap.periodKey);
    out.put("unlimited", snap.unlimited);
    out.put("allowed", snap.allowed);
    out.put("reason", snap.reason);
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> validateFeature(String organizationId, Map<String, Object> body) {
    String featureCode = requireCode(body, "featureCode");
    Map<String, Object> flag = subscriptionService.flag(organizationId, featureCode);
    boolean enabled = Boolean.TRUE.equals(flag.get("enabled"));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("featureCode", featureCode);
    out.put("planId", flag.get("planId"));
    out.put("allowed", enabled);
    out.put("reason", enabled ? "feature enabled on plan" : "feature disabled or missing on plan");
    return out;
  }

  @Transactional
  public Map<String, Object> incrementUsage(
      String organizationId, Map<String, Object> body, String actor) {
    return applyDelta(organizationId, body, actor, true);
  }

  @Transactional
  public Map<String, Object> decrementUsage(
      String organizationId, Map<String, Object> body, String actor) {
    return applyDelta(organizationId, body, actor, false);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> recentEvents(String organizationId, String limitCode) {
    List<UsageEventEntity> events =
        StringUtils.hasText(limitCode)
            ? usageEventRepository.findTop50ByOrganizationIdAndLimitCodeOrderByCreatedAtDesc(
                organizationId, limitCode)
            : usageEventRepository.findTop50ByOrganizationIdOrderByCreatedAtDesc(organizationId);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (UsageEventEntity e : events) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", e.getId());
      m.put("limitCode", e.getLimitCode());
      m.put("periodKey", e.getPeriodKey());
      m.put("delta", e.getDelta());
      m.put("usedAfter", e.getUsedAfter());
      m.put("reason", e.getReason());
      m.put("actor", e.getActor());
      m.put("createdAt", e.getCreatedAt());
      rows.add(m);
    }
    return rows;
  }

  private Map<String, Object> applyDelta(
      String organizationId, Map<String, Object> body, String actor, boolean increment) {
    String limitCode = requireCode(body, "limitCode");
    long magnitude = parseLong(body.get("delta"), 1L);
    if (magnitude <= 0) {
      throw new IllegalArgumentException("delta must be > 0");
    }
    long delta = increment ? magnitude : -magnitude;
    boolean enforce =
        body != null
            && body.get("enforce") != null
            && Boolean.parseBoolean(String.valueOf(body.get("enforce")));

    if (increment && enforce) {
      LimitSnapshot check = snapshot(organizationId, limitCode, magnitude);
      if (!check.allowed) {
        Map<String, Object> denied = new LinkedHashMap<>();
        denied.put("organizationId", organizationId);
        denied.put("limitCode", limitCode);
        denied.put("delta", magnitude);
        denied.put("applied", false);
        denied.put("allowed", false);
        denied.put("reason", check.reason);
        denied.put("limit", check.limit);
        denied.put("used", check.used);
        denied.put("remaining", check.remaining);
        denied.put("periodKey", check.periodKey);
        return denied;
      }
    }

    String periodKey = resolvePeriodKey(limitCode);
    UsageCounterEntity counter =
        usageCounterRepository
            .findByOrganizationIdAndLimitCodeAndPeriodKey(organizationId, limitCode, periodKey)
            .orElseGet(
                () -> {
                  UsageCounterEntity c = new UsageCounterEntity();
                  c.setOrganizationId(organizationId);
                  c.setLimitCode(limitCode);
                  c.setPeriodKey(periodKey);
                  c.setUsedValue(0L);
                  return c;
                });

    long next = counter.getUsedValue() + delta;
    if (next < 0) {
      next = 0;
    }
    counter.setUsedValue(next);
    counter.setUpdatedAt(Instant.now());
    usageCounterRepository.save(counter);

    UsageEventEntity event = new UsageEventEntity();
    event.setOrganizationId(organizationId);
    event.setLimitCode(limitCode);
    event.setPeriodKey(periodKey);
    event.setDelta(delta);
    event.setUsedAfter(next);
    event.setReason(body != null && body.get("reason") != null ? String.valueOf(body.get("reason")) : null);
    event.setActor(actor);
    event.setCreatedAt(Instant.now());
    usageEventRepository.save(event);

    LimitSnapshot snap = snapshot(organizationId, limitCode, 0L);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("limitCode", limitCode);
    out.put("delta", delta);
    out.put("applied", true);
    out.put("used", next);
    out.put("periodKey", periodKey);
    out.put("limit", snap.limit);
    out.put("remaining", snap.remaining);
    out.put("unlimited", snap.unlimited);
    out.put("allowed", snap.allowed || !enforce);
    out.put("reason", "usage updated");
    return out;
  }

  private Map<String, Object> limitSnapshot(
      String organizationId, String limitCode, Long limit, long delta) {
    LimitSnapshot snap = snapshot(organizationId, limitCode, delta);
    // Prefer provided plan limit when iterating entitlements map.
    if (limit != null) {
      snap = snapshotWithLimit(organizationId, limitCode, limit, delta);
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("limitCode", limitCode);
    m.put("limit", snap.limit);
    m.put("used", snap.used);
    m.put("remaining", snap.remaining);
    m.put("periodKey", snap.periodKey);
    m.put("unlimited", snap.unlimited);
    m.put("allowed", snap.allowed);
    return m;
  }

  private LimitSnapshot snapshot(String organizationId, String limitCode, long delta) {
    Map<String, Object> entitlements = subscriptionService.entitlements(organizationId);
    @SuppressWarnings("unchecked")
    Map<String, Long> limits = (Map<String, Long>) entitlements.get("limits");
    Long limit = limits == null ? null : limits.get(limitCode);
    LimitSnapshot snap = snapshotWithLimit(organizationId, limitCode, limit, delta);
    snap.planId = String.valueOf(entitlements.get("planId"));
    return snap;
  }

  private LimitSnapshot snapshotWithLimit(
      String organizationId, String limitCode, Long limit, long delta) {
    String periodKey = resolvePeriodKey(limitCode);
    long used =
        usageCounterRepository
            .findByOrganizationIdAndLimitCodeAndPeriodKey(organizationId, limitCode, periodKey)
            .map(UsageCounterEntity::getUsedValue)
            .orElse(0L);

    LimitSnapshot snap = new LimitSnapshot();
    snap.periodKey = periodKey;
    snap.used = used;
    snap.limit = limit;
    if (limit == null) {
      snap.unlimited = true;
      snap.remaining = null;
      snap.allowed = true;
      snap.reason = "limit not defined on plan (treated as unlimited)";
      return snap;
    }
    if (limit < 0) {
      snap.unlimited = true;
      snap.remaining = null;
      snap.allowed = true;
      snap.reason = "unlimited (-1)";
      return snap;
    }
    snap.unlimited = false;
    long remaining = limit - used;
    snap.remaining = Math.max(0, remaining);
    boolean allowed = used + delta <= limit;
    snap.allowed = allowed;
    snap.reason =
        allowed
            ? "within limit"
            : "would exceed limit (used=" + used + ", delta=" + delta + ", limit=" + limit + ")";
    return snap;
  }

  private String resolvePeriodKey(String limitCode) {
    Optional<LimitDefinitionEntity> def = limitDefinitionRepository.findById(limitCode);
    if (def.isPresent()) {
      String aggregation =
          def.get().getAggregation() == null
              ? "NUMERIC"
              : def.get().getAggregation().trim().toUpperCase(Locale.ROOT);
      if ("MONTHLY".equals(aggregation) || "PERIOD".equals(aggregation)) {
        return YearMonth.now(ZoneOffset.UTC).toString();
      }
    }
    return PERIOD_ALL;
  }

  private static String requireCode(Map<String, Object> body, String key) {
    if (body == null || body.get(key) == null || !StringUtils.hasText(String.valueOf(body.get(key)))) {
      throw new IllegalArgumentException(key + " is required");
    }
    return String.valueOf(body.get(key)).trim();
  }

  private static long parseLong(Object raw, long defaultValue) {
    if (raw == null) {
      return defaultValue;
    }
    return Long.parseLong(String.valueOf(raw));
  }

  private static final class LimitSnapshot {
    String planId;
    Long limit;
    long used;
    Long remaining;
    String periodKey;
    boolean unlimited;
    boolean allowed;
    String reason;
  }
}
