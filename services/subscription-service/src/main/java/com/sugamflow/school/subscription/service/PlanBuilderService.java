package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 11 plan builder: draft versions, publish to live plan JSON, schedule list prices. Does not
 * change entitlement read contracts — publish writes via {@link SubscriptionService#savePlan}.
 */
@Service
public class PlanBuilderService {

  private final PlanVersionRepository versionRepository;
  private final PlanPriceScheduleRepository scheduleRepository;
  private final SubscriptionPlanRepository planRepository;
  private final SubscriptionService subscriptionService;
  private final PlanPriceRepository planPriceRepository;
  private final PriceBookRepository priceBookRepository;
  private final BillingCycleRepository billingCycleRepository;

  public PlanBuilderService(
      PlanVersionRepository versionRepository,
      PlanPriceScheduleRepository scheduleRepository,
      SubscriptionPlanRepository planRepository,
      SubscriptionService subscriptionService,
      PlanPriceRepository planPriceRepository,
      PriceBookRepository priceBookRepository,
      BillingCycleRepository billingCycleRepository) {
    this.versionRepository = versionRepository;
    this.scheduleRepository = scheduleRepository;
    this.planRepository = planRepository;
    this.subscriptionService = subscriptionService;
    this.planPriceRepository = planPriceRepository;
    this.priceBookRepository = priceBookRepository;
    this.billingCycleRepository = billingCycleRepository;
  }

  public List<Map<String, Object>> listVersions(String planId) {
    requirePlan(planId);
    return versionRepository.findByPlanIdOrderByVersionNumberDesc(planId).stream()
        .map(this::versionToMap)
        .toList();
  }

  public Map<String, Object> getVersion(String planId, long versionId) {
    return versionToMap(requireVersion(planId, versionId));
  }

  /** Get open DRAFT or create one cloned from live plan (or latest published). */
  @Transactional
  public Map<String, Object> ensureDraft(String planId) {
    requirePlan(planId);
    return versionRepository
        .findFirstByPlanIdAndStatusIgnoreCaseOrderByVersionNumberDesc(planId, "DRAFT")
        .map(this::versionToMap)
        .orElseGet(() -> createDraftFromLive(planId, Map.of("label", "Draft")));
  }

  @Transactional
  public Map<String, Object> createDraft(String planId, Map<String, Object> body) {
    requirePlan(planId);
    versionRepository
        .findFirstByPlanIdAndStatusIgnoreCaseOrderByVersionNumberDesc(planId, "DRAFT")
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException(
                  "Draft already exists as v" + existing.getVersionNumber() + " (id=" + existing.getId() + ")");
            });
    return createDraftFromLive(planId, body == null ? Map.of() : body);
  }

  @Transactional
  public Map<String, Object> saveDraft(String planId, long versionId, Map<String, Object> body) {
    PlanVersionEntity version = requireVersion(planId, versionId);
    if (!"DRAFT".equalsIgnoreCase(version.getStatus())) {
      throw new IllegalArgumentException("Only DRAFT versions can be edited (status=" + version.getStatus() + ")");
    }
    if (body.containsKey("label")) {
      version.setLabel(str(body.get("label")));
    }
    if (body.containsKey("notes")) {
      version.setNotes(str(body.get("notes")));
    }
    if (body.containsKey("featureFlags")) {
      version.setFeatureFlagsJson(toObjectMap(body.get("featureFlags")));
    }
    if (body.containsKey("limits")) {
      version.setLimitsJson(toObjectMap(body.get("limits")));
    }
    if (body.containsKey("moduleOrder")) {
      version.setModuleOrderJson(toStringList(body.get("moduleOrder")));
    }
    version.setUpdatedAt(Instant.now());
    return versionToMap(versionRepository.save(version));
  }

  /** Publish draft (or specified version) onto live subscription_plan + projection. */
  @Transactional
  public Map<String, Object> publish(String planId, Map<String, Object> body) {
    requirePlan(planId);
    PlanVersionEntity version;
    if (body != null && body.get("versionId") != null) {
      version = requireVersion(planId, asLong(body.get("versionId"), -1));
    } else {
      version =
          versionRepository
              .findFirstByPlanIdAndStatusIgnoreCaseOrderByVersionNumberDesc(planId, "DRAFT")
              .orElseThrow(() -> new IllegalArgumentException("No DRAFT version to publish for " + planId));
    }
    if (!"DRAFT".equalsIgnoreCase(version.getStatus())
        && !"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
      throw new IllegalArgumentException("Cannot publish status=" + version.getStatus());
    }
    if ("PUBLISHED".equalsIgnoreCase(version.getStatus())) {
      Map<String, Object> out = new LinkedHashMap<>(versionToMap(version));
      out.put("idempotent", true);
      return out;
    }

    SubscriptionPlan live = subscriptionService.getPlan(planId);
    if (live == null) {
      throw new IllegalArgumentException("Unknown plan: " + planId);
    }
    live.setFeatureFlags(toBooleanMap(version.getFeatureFlagsJson()));
    live.setLimits(toLongMap(version.getLimitsJson()));
    subscriptionService.savePlan(live);

    for (PlanVersionEntity prior :
        versionRepository.findByPlanIdOrderByVersionNumberDesc(planId)) {
      if ("PUBLISHED".equalsIgnoreCase(prior.getStatus()) && !prior.getId().equals(version.getId())) {
        prior.setStatus("ARCHIVED");
        prior.setUpdatedAt(Instant.now());
        versionRepository.save(prior);
      }
    }

    Instant now = Instant.now();
    version.setStatus("PUBLISHED");
    version.setPublishedAt(now);
    version.setUpdatedAt(now);
    if (body != null && body.get("notes") != null) {
      version.setNotes(str(body.get("notes")));
    }
    versionRepository.save(version);

    applyDueSchedules();

    Map<String, Object> out = new LinkedHashMap<>(versionToMap(version));
    out.put("livePlan", live);
    out.put("idempotent", false);
    return out;
  }

  public List<Map<String, Object>> listPriceSchedules(String planId) {
    requirePlan(planId);
    applyDueSchedules();
    return scheduleRepository.findByPlanIdOrderByEffectiveAtAsc(planId).stream()
        .map(this::scheduleToMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> schedulePrice(String planId, Map<String, Object> body) {
    requirePlan(planId);
    final String resolvedBookId;
    String requestedBookId = str(body.get("priceBookId"));
    if (requestedBookId == null || requestedBookId.isBlank()) {
      PriceBookEntity def =
          priceBookRepository
              .findFirstByDefaultBookTrueAndActiveTrue()
              .orElseThrow(() -> new IllegalArgumentException("No default price book"));
      resolvedBookId = def.getId();
    } else {
      priceBookRepository
          .findById(requestedBookId)
          .orElseThrow(() -> new IllegalArgumentException("Unknown priceBookId: " + requestedBookId));
      resolvedBookId = requestedBookId;
    }
    String cycle = requireStr(body.get("billingCycleCode"), "billingCycleCode").toUpperCase(Locale.ROOT);
    billingCycleRepository
        .findById(cycle)
        .filter(c -> c.isActive())
        .orElseThrow(() -> new IllegalArgumentException("Unknown billing cycle: " + cycle));

    Instant effectiveAt = Instant.parse(requireStr(body.get("effectiveAt"), "effectiveAt"));
    long amountMinor = asLong(body.get("amountMinor"), -1);
    if (amountMinor < 0) {
      throw new IllegalArgumentException("amountMinor must be >= 0");
    }

    PlanPriceScheduleEntity row = new PlanPriceScheduleEntity();
    row.setPlanId(planId);
    row.setPriceBookId(resolvedBookId);
    row.setBillingCycleCode(cycle);
    row.setAmountMinor(amountMinor);
    row.setCurrency(strOr(body.get("currency"), "INR"));
    row.setEffectiveAt(effectiveAt);
    row.setStatus("SCHEDULED");
    row.setNotes(str(body.get("notes")));
    row.setCreatedAt(Instant.now());
    scheduleRepository.save(row);

    if (!effectiveAt.isAfter(Instant.now())) {
      applySchedule(row);
    }
    return scheduleToMap(row);
  }

  @Transactional
  public Map<String, Object> cancelPriceSchedule(String planId, long scheduleId) {
    PlanPriceScheduleEntity row =
        scheduleRepository
            .findById(scheduleId)
            .filter(s -> planId.equals(s.getPlanId()))
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
    if ("APPLIED".equalsIgnoreCase(row.getStatus())) {
      throw new IllegalArgumentException("Cannot cancel an APPLIED schedule");
    }
    row.setStatus("CANCELLED");
    return scheduleToMap(scheduleRepository.save(row));
  }

  @Transactional
  public Map<String, Object> applyDueSchedules() {
    Instant now = Instant.now();
    List<PlanPriceScheduleEntity> due =
        scheduleRepository.findByStatusIgnoreCaseAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
            "SCHEDULED", now);
    int applied = 0;
    for (PlanPriceScheduleEntity row : due) {
      applySchedule(row);
      applied++;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("appliedCount", applied);
    out.put("asOf", now.toString());
    return out;
  }

  private void applySchedule(PlanPriceScheduleEntity row) {
    PlanPriceEntity price =
        planPriceRepository
            .findByPriceBookIdAndPlanIdAndBillingCycleCode(
                row.getPriceBookId(), row.getPlanId(), row.getBillingCycleCode())
            .orElseGet(PlanPriceEntity::new);
    if (price.getId() == null) {
      price.setPriceBookId(row.getPriceBookId());
      price.setPlanId(row.getPlanId());
      price.setBillingCycleCode(row.getBillingCycleCode());
    }
    price.setAmountMinor(row.getAmountMinor());
    price.setCurrency(row.getCurrency());
    price.setActive(true);
    price.setUpdatedAt(Instant.now());
    planPriceRepository.save(price);

    Instant now = Instant.now();
    row.setStatus("APPLIED");
    row.setAppliedAt(now);
    scheduleRepository.save(row);
  }

  private Map<String, Object> createDraftFromLive(String planId, Map<String, Object> body) {
    SubscriptionPlan live = subscriptionService.getPlan(planId);
    if (live == null) {
      throw new IllegalArgumentException("Unknown plan: " + planId);
    }
    int next = versionRepository.maxVersionNumber(planId) + 1;
    PlanVersionEntity version = new PlanVersionEntity();
    version.setPlanId(planId);
    version.setVersionNumber(next);
    version.setStatus("DRAFT");
    version.setLabel(strOr(body.get("label"), "Draft v" + next));
    version.setNotes(str(body.get("notes")));
    version.setFeatureFlagsJson(new LinkedHashMap<>(toObjectMap(live.getFeatureFlags())));
    version.setLimitsJson(new LinkedHashMap<>(toObjectMap(live.getLimits())));
    if (body.containsKey("moduleOrder")) {
      version.setModuleOrderJson(toStringList(body.get("moduleOrder")));
    } else {
      version.setModuleOrderJson(new ArrayList<>());
    }
    Instant now = Instant.now();
    version.setCreatedAt(now);
    version.setUpdatedAt(now);
    return versionToMap(versionRepository.save(version));
  }

  private void requirePlan(String planId) {
    if (!planRepository.existsById(planId)) {
      throw new IllegalArgumentException("Unknown plan: " + planId);
    }
  }

  private PlanVersionEntity requireVersion(String planId, long versionId) {
    return versionRepository
        .findByIdAndPlanId(versionId, planId)
        .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));
  }

  private Map<String, Object> versionToMap(PlanVersionEntity v) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", v.getId());
    map.put("planId", v.getPlanId());
    map.put("versionNumber", v.getVersionNumber());
    map.put("status", v.getStatus());
    map.put("label", v.getLabel());
    map.put("notes", v.getNotes());
    map.put("featureFlags", v.getFeatureFlagsJson());
    map.put("limits", v.getLimitsJson());
    map.put("moduleOrder", v.getModuleOrderJson());
    map.put("createdAt", v.getCreatedAt());
    map.put("updatedAt", v.getUpdatedAt());
    map.put("publishedAt", v.getPublishedAt());
    return map;
  }

  private Map<String, Object> scheduleToMap(PlanPriceScheduleEntity s) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", s.getId());
    map.put("planId", s.getPlanId());
    map.put("priceBookId", s.getPriceBookId());
    map.put("billingCycleCode", s.getBillingCycleCode());
    map.put("amountMinor", s.getAmountMinor());
    map.put("currency", s.getCurrency());
    map.put("effectiveAt", s.getEffectiveAt());
    map.put("status", s.getStatus());
    map.put("appliedAt", s.getAppliedAt());
    map.put("notes", s.getNotes());
    map.put("createdAt", s.getCreatedAt());
    return map;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toObjectMap(Object raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (raw instanceof Map<?, ?> map) {
      map.forEach((k, v) -> out.put(String.valueOf(k), v));
    }
    return out;
  }

  private static Map<String, Boolean> toBooleanMap(Map<String, Object> raw) {
    Map<String, Boolean> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    raw.forEach((k, v) -> out.put(k, v != null && Boolean.parseBoolean(String.valueOf(v))));
    return out;
  }

  private static Map<String, Long> toLongMap(Map<String, Object> raw) {
    Map<String, Long> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    raw.forEach(
        (k, v) -> {
          if (v == null) {
            out.put(k, null);
          } else if (v instanceof Number n) {
            out.put(k, n.longValue());
          } else {
            out.put(k, Long.valueOf(String.valueOf(v)));
          }
        });
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<String> toStringList(Object raw) {
    List<String> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object o : list) {
        if (o != null && !String.valueOf(o).isBlank()) {
          out.add(String.valueOf(o));
        }
      }
    }
    return out;
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
