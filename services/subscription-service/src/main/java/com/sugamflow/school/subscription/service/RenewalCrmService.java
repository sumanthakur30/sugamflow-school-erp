package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.persistence.entity.AddonDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.RenewalOpportunityEntity;
import com.sugamflow.school.subscription.persistence.entity.RenewalReminderEntity;
import com.sugamflow.school.subscription.persistence.entity.SubscriptionPaymentEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionLifecycleEntity;
import com.sugamflow.school.subscription.persistence.entity.UsageCounterEntity;
import com.sugamflow.school.subscription.persistence.repo.AddonDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.RenewalOpportunityRepository;
import com.sugamflow.school.subscription.persistence.repo.RenewalReminderRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPaymentRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionLifecycleRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import com.sugamflow.school.subscription.persistence.repo.UsageCounterRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 12 CRM renewals: pipeline sync, health score, T-30/14/7 reminders, upsell hints. Read/write
 * additive tables only; does not change entitlement JSON contracts.
 */
@Service
public class RenewalCrmService {

  private static final Set<String> VALID_STAGES =
      Set.of("LEAD", "TRIAL", "ACTIVE", "RENEWAL_DUE", "AT_RISK", "WON", "LOST", "CHURNED");

  private final RenewalOpportunityRepository opportunityRepository;
  private final RenewalReminderRepository reminderRepository;
  private final TenantSubscriptionRepository tenantSubscriptionRepository;
  private final TenantSubscriptionLifecycleRepository lifecycleRepository;
  private final SubscriptionPaymentRepository paymentRepository;
  private final UsageCounterRepository usageCounterRepository;
  private final AddonDefinitionRepository addonDefinitionRepository;
  private final SubscriptionService subscriptionService;
  private final SubscriptionLifecycleService lifecycleService;

  public RenewalCrmService(
      RenewalOpportunityRepository opportunityRepository,
      RenewalReminderRepository reminderRepository,
      TenantSubscriptionRepository tenantSubscriptionRepository,
      TenantSubscriptionLifecycleRepository lifecycleRepository,
      SubscriptionPaymentRepository paymentRepository,
      UsageCounterRepository usageCounterRepository,
      AddonDefinitionRepository addonDefinitionRepository,
      SubscriptionService subscriptionService,
      SubscriptionLifecycleService lifecycleService) {
    this.opportunityRepository = opportunityRepository;
    this.reminderRepository = reminderRepository;
    this.tenantSubscriptionRepository = tenantSubscriptionRepository;
    this.lifecycleRepository = lifecycleRepository;
    this.paymentRepository = paymentRepository;
    this.usageCounterRepository = usageCounterRepository;
    this.addonDefinitionRepository = addonDefinitionRepository;
    this.subscriptionService = subscriptionService;
    this.lifecycleService = lifecycleService;
  }

  @Transactional
  public Map<String, Object> syncPipeline() {
    int upserted = 0;
    for (TenantSubscriptionEntity tenant : tenantSubscriptionRepository.findAll()) {
      upsertOpportunity(tenant.getOrganizationId());
      upserted++;
    }
    int reminders = generateRemindersInternal();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("opportunitiesUpserted", upserted);
    out.put("remindersCreated", reminders);
    out.put("asOf", Instant.now().toString());
    return out;
  }

  public Map<String, Object> dashboard() {
    syncLight();
    Map<String, Object> stages = new LinkedHashMap<>();
    for (Object[] row : opportunityRepository.countGroupedByStage()) {
      stages.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
    }
    Instant now = Instant.now();
    List<Map<String, Object>> allOpps = new ArrayList<>();
    for (RenewalOpportunityEntity opp :
        opportunityRepository.findAllByOrderByNextActionAtAscHealthScoreAsc()) {
      TenantSubscriptionLifecycleEntity life =
          lifecycleRepository.findById(opp.getOrganizationId()).orElse(null);
      Instant expires = life == null ? null : life.getExpiresAt();
      Map<String, Object> row = opportunityToMap(opp);
      row.put("expiresAt", expires);
      row.put(
          "daysUntilExpiry",
          expires == null ? null : ChronoUnit.DAYS.between(now, expires));
      row.put("lifecycleStatus", life == null ? "ACTIVE" : life.getStatus());
      row.put("upsellHints", List.of());
      allOpps.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("asOf", Instant.now().toString());
    out.put("stageCounts", stages);
    out.put("pendingReminders", reminderRepository.countByStatusIgnoreCase("PENDING"));
    out.put("pipeline", pipeline(30));
    out.put("opportunities", allOpps);
    out.put("dueReminders", listReminders("PENDING", 50));
    return out;
  }

  public List<Map<String, Object>> pipeline(int withinDays) {
    return pipeline(withinDays, null);
  }

  /** When {@code stage} is set, return all opportunities in that stage (stage-box click filter). */
  public List<Map<String, Object>> pipeline(int withinDays, String stage) {
    if (stage != null && !stage.isBlank()) {
      return listByStage(stage.trim());
    }
    int days = Math.max(1, Math.min(withinDays, 365));
    Instant now = Instant.now();
    Instant horizon = now.plus(days, ChronoUnit.DAYS);

    List<Map<String, Object>> rows = new ArrayList<>();
    for (TenantSubscriptionEntity tenant : tenantSubscriptionRepository.findAll()) {
      RenewalOpportunityEntity opp = upsertOpportunity(tenant.getOrganizationId());
      TenantSubscriptionLifecycleEntity life =
          lifecycleRepository.findById(tenant.getOrganizationId()).orElse(null);
      Instant expires = life == null ? null : life.getExpiresAt();
      boolean inWindow =
          expires != null && !expires.isBefore(now) && expires.isBefore(horizon);
      boolean atRisk =
          "AT_RISK".equalsIgnoreCase(opp.getStage())
              || "GRACE".equalsIgnoreCase(life == null ? "" : life.getStatus())
              || "EXPIRED".equalsIgnoreCase(life == null ? "" : life.getStatus());
      if (!inWindow && !atRisk && !"RENEWAL_DUE".equalsIgnoreCase(opp.getStage())) {
        continue;
      }
      rows.add(enrichPipelineRow(opp, life, now));
    }
    rows.sort(
        Comparator.comparingLong(
            (Map<String, Object> m) -> {
              Object d = m.get("daysUntilExpiry");
              return d == null ? Long.MAX_VALUE : ((Number) d).longValue();
            }));
    return rows;
  }

  public List<Map<String, Object>> listByStage(String stage) {
    String normalized = stage.toUpperCase(Locale.ROOT);
    if (!VALID_STAGES.contains(normalized)) {
      throw new IllegalArgumentException("Invalid stage: " + stage);
    }
    Instant now = Instant.now();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (RenewalOpportunityEntity opp :
        opportunityRepository.findByStageIgnoreCaseOrderByHealthScoreAsc(normalized)) {
      TenantSubscriptionLifecycleEntity life =
          lifecycleRepository.findById(opp.getOrganizationId()).orElse(null);
      rows.add(enrichPipelineRow(opp, life, now));
    }
    return rows;
  }

  private Map<String, Object> enrichPipelineRow(
      RenewalOpportunityEntity opp, TenantSubscriptionLifecycleEntity life, Instant now) {
    Instant expires = life == null ? null : life.getExpiresAt();
    Map<String, Object> row = opportunityToMap(opp);
    row.put("expiresAt", expires);
    row.put(
        "daysUntilExpiry",
        expires == null ? null : ChronoUnit.DAYS.between(now, expires));
    row.put("lifecycleStatus", life == null ? "ACTIVE" : life.getStatus());
    row.put("upsellHints", upsellHints(opp.getOrganizationId()));
    return row;
  }

  public Map<String, Object> health(String organizationId) {
    Map<String, Object> scored = computeHealth(organizationId);
    RenewalOpportunityEntity opp = upsertOpportunity(organizationId);
    opp.setHealthScore(((Number) scored.get("score")).intValue());
    opp.setUpdatedAt(Instant.now());
    opportunityRepository.save(opp);
    scored.put("opportunityId", opp.getId());
    scored.put("stage", opp.getStage());
    return scored;
  }

  public List<Map<String, Object>> upsellHints(String organizationId) {
    List<Map<String, Object>> hints = new ArrayList<>();
    String planId =
        tenantSubscriptionRepository
            .findById(organizationId)
            .map(TenantSubscriptionEntity::getPlanId)
            .orElse("starter");
    SubscriptionPlan plan = subscriptionService.getPlan(planId);
    Map<String, Long> limits = plan == null ? Map.of() : plan.getLimits();

    Map<String, String> meterToSku = new LinkedHashMap<>();
    for (AddonDefinitionEntity addon : addonDefinitionRepository.findByActiveTrueOrderBySortOrderAsc()) {
      if (addon.getMeterCode() != null && !addon.getMeterCode().isBlank()) {
        meterToSku.putIfAbsent(addon.getMeterCode(), addon.getSku());
      }
    }

    for (UsageCounterEntity u :
        usageCounterRepository.findByOrganizationIdOrderByLimitCodeAscPeriodKeyAsc(organizationId)) {
      Long cap = resolveCap(limits, u.getLimitCode());
      if (cap == null || cap <= 0) {
        continue;
      }
      double pct = (100.0 * u.getUsedValue()) / cap;
      if (pct < 80) {
        continue;
      }
      Map<String, Object> hint = new LinkedHashMap<>();
      hint.put("type", "NEAR_LIMIT");
      hint.put("limitCode", u.getLimitCode());
      hint.put("used", u.getUsedValue());
      hint.put("cap", cap);
      hint.put("utilizationPct", Math.round(pct));
      hint.put("suggestedSku", meterToSku.get(u.getLimitCode()));
      hint.put(
          "message",
          u.getLimitCode()
              + " at "
              + Math.round(pct)
              + "% — consider add-on "
              + meterToSku.getOrDefault(u.getLimitCode(), "pack"));
      hints.add(hint);
    }

    if (plan != null && plan.getFeatureFlags() != null) {
      long enabled =
          plan.getFeatureFlags().values().stream().filter(Boolean.TRUE::equals).count();
      long total = plan.getFeatureFlags().size();
      if (total > 0 && enabled < total * 0.4) {
        Map<String, Object> hint = new LinkedHashMap<>();
        hint.put("type", "LOW_FEATURE_ADOPTION");
        hint.put("enabledFeatures", enabled);
        hint.put("totalFeatures", total);
        hint.put(
            "message",
            "Only "
                + enabled
                + "/"
                + total
                + " plan features enabled — enable unused paid capabilities or downsell");
        hints.add(hint);
      }
    }
    return hints;
  }

  @Transactional
  public Map<String, Object> updateOpportunity(String organizationId, Map<String, Object> body) {
    RenewalOpportunityEntity opp = upsertOpportunity(organizationId);
    if (body.containsKey("stage")) {
      String stage = String.valueOf(body.get("stage")).trim().toUpperCase(Locale.ROOT);
      if (!VALID_STAGES.contains(stage)) {
        throw new IllegalArgumentException("Invalid stage: " + stage);
      }
      opp.setStage(stage);
    }
    if (body.containsKey("owner")) {
      opp.setOwner(str(body.get("owner")));
    }
    if (body.containsKey("notes")) {
      opp.setNotes(str(body.get("notes")));
    }
    if (body.containsKey("nextActionAt") && body.get("nextActionAt") != null) {
      opp.setNextActionAt(Instant.parse(String.valueOf(body.get("nextActionAt"))));
    }
    if (body.containsKey("healthScore")) {
      int score = (int) asLong(body.get("healthScore"), opp.getHealthScore());
      opp.setHealthScore(Math.max(0, Math.min(100, score)));
    }
    opp.setUpdatedAt(Instant.now());
    return opportunityToMap(opportunityRepository.save(opp));
  }

  @Transactional
  public Map<String, Object> generateReminders() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("created", generateRemindersInternal());
    out.put("pending", reminderRepository.countByStatusIgnoreCase("PENDING"));
    return out;
  }

  public List<Map<String, Object>> listReminders(String status, int limit) {
    int top = Math.max(1, Math.min(limit, 200));
    String st = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
    return reminderRepository.findByStatusIgnoreCaseOrderByDueAtAsc(st).stream()
        .limit(top)
        .map(this::reminderToMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> markReminder(long reminderId, String status) {
    RenewalReminderEntity row =
        reminderRepository
            .findById(reminderId)
            .orElseThrow(() -> new IllegalArgumentException("Reminder not found: " + reminderId));
    String st = status == null ? "SENT" : status.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("SENT", "SKIPPED", "CANCELLED", "PENDING").contains(st)) {
      throw new IllegalArgumentException("Invalid reminder status: " + st);
    }
    row.setStatus(st);
    if ("SENT".equals(st)) {
      row.setSentAt(Instant.now());
    }
    return reminderToMap(reminderRepository.save(row));
  }

  private void syncLight() {
    for (TenantSubscriptionEntity tenant : tenantSubscriptionRepository.findAll()) {
      upsertOpportunity(tenant.getOrganizationId());
    }
  }

  private RenewalOpportunityEntity upsertOpportunity(String organizationId) {
    Optional<RenewalOpportunityEntity> existing =
        opportunityRepository.findByOrganizationId(organizationId);
    TenantSubscriptionEntity tenant =
        tenantSubscriptionRepository.findById(organizationId).orElse(null);
    String planId = tenant == null ? null : tenant.getPlanId();
    TenantSubscriptionLifecycleEntity life =
        lifecycleRepository.findById(organizationId).orElse(null);
    lifecycleService.ensureActiveForever(organizationId);
    life = lifecycleRepository.findById(organizationId).orElse(life);

    Map<String, Object> scored = computeHealth(organizationId);
    String stage = deriveStage(life, scored);

    RenewalOpportunityEntity opp = existing.orElseGet(RenewalOpportunityEntity::new);
    if (opp.getId() == null) {
      opp.setOrganizationId(organizationId);
      opp.setCreatedAt(Instant.now());
    }
    opp.setPlanId(planId);
    opp.setStage(stage);
    opp.setHealthScore(((Number) scored.get("score")).intValue());
    if (life != null && life.getExpiresAt() != null) {
      long days = ChronoUnit.DAYS.between(Instant.now(), life.getExpiresAt());
      if (days <= 30) {
        opp.setNextActionAt(life.getExpiresAt().minus(7, ChronoUnit.DAYS));
      }
    }
    opp.setUpdatedAt(Instant.now());
    return opportunityRepository.save(opp);
  }

  private String deriveStage(TenantSubscriptionLifecycleEntity life, Map<String, Object> scored) {
    if (life == null) {
      return "ACTIVE";
    }
    String status = life.getStatus() == null ? "ACTIVE" : life.getStatus().toUpperCase(Locale.ROOT);
    if ("TRIAL".equals(status)) {
      return "TRIAL";
    }
    if ("EXPIRED".equals(status) || "CANCELLED".equals(status)) {
      return "CHURNED";
    }
    if ("SUSPENDED".equals(status) || "GRACE".equals(status)) {
      return "AT_RISK";
    }
    Instant expires = life.getExpiresAt();
    if (expires != null) {
      long days = ChronoUnit.DAYS.between(Instant.now(), expires);
      if (days <= 30 && days >= 0) {
        return "RENEWAL_DUE";
      }
      if (days < 0) {
        return "AT_RISK";
      }
    }
    int score = ((Number) scored.get("score")).intValue();
    if (score < 40) {
      return "AT_RISK";
    }
    return "ACTIVE";
  }

  private Map<String, Object> computeHealth(String organizationId) {
    Instant now = Instant.now();
    Instant ago90 = now.minus(90, ChronoUnit.DAYS);
    TenantSubscriptionLifecycleEntity life =
        lifecycleRepository.findById(organizationId).orElse(null);

    int score = 70;
    List<Map<String, Object>> factors = new ArrayList<>();

    if (life != null && life.getExpiresAt() != null) {
      long days = ChronoUnit.DAYS.between(now, life.getExpiresAt());
      int delta;
      if (days < 0) {
        delta = -30;
      } else if (days <= 7) {
        delta = -20;
      } else if (days <= 14) {
        delta = -10;
      } else if (days <= 30) {
        delta = -5;
      } else {
        delta = 5;
      }
      score += delta;
      factors.add(factor("daysToExpiry", days, delta));
    } else {
      score += 10;
      factors.add(factor("noExpiry", "forever", 10));
    }

    String status = life == null ? "ACTIVE" : life.getStatus();
    if ("GRACE".equalsIgnoreCase(status)) {
      score -= 25;
      factors.add(factor("lifecycle", "GRACE", -25));
    } else if ("EXPIRED".equalsIgnoreCase(status) || "SUSPENDED".equalsIgnoreCase(status)) {
      score -= 40;
      factors.add(factor("lifecycle", status, -40));
    }

    List<SubscriptionPaymentEntity> pays = paymentRepository.findSuccessfulSince(ago90);
    long successOrg =
        pays.stream().filter(p -> organizationId.equals(p.getOrganizationId())).count();
    long failed =
        paymentRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
            .filter(p -> "FAILED".equalsIgnoreCase(p.getStatus()))
            .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(ago90))
            .count();
    if (successOrg > 0) {
      score += 10;
      factors.add(factor("paymentsSuccess90d", successOrg, 10));
    }
    if (failed > 0) {
      int delta = (int) Math.min(20, failed * 8);
      score -= delta;
      factors.add(factor("paymentsFailed90d", failed, -delta));
    }

    List<UsageCounterEntity> usage =
        usageCounterRepository.findByOrganizationIdOrderByLimitCodeAscPeriodKeyAsc(organizationId);
    long usedMeters = usage.stream().filter(u -> u.getUsedValue() > 0).count();
    if (usedMeters >= 3) {
      score += 8;
      factors.add(factor("usageDepth", usedMeters, 8));
    } else if (usedMeters == 0) {
      score -= 8;
      factors.add(factor("usageDepth", 0, -8));
    }

    score = Math.max(0, Math.min(100, score));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("score", score);
    out.put("band", score >= 70 ? "HEALTHY" : score >= 40 ? "WATCH" : "CRITICAL");
    out.put("factors", factors);
    return out;
  }

  private int generateRemindersInternal() {
    Instant now = Instant.now();
    int created = 0;
    for (TenantSubscriptionEntity tenant : tenantSubscriptionRepository.findAll()) {
      TenantSubscriptionLifecycleEntity life =
          lifecycleRepository.findById(tenant.getOrganizationId()).orElse(null);
      if (life == null || life.getExpiresAt() == null) {
        continue;
      }
      RenewalOpportunityEntity opp = upsertOpportunity(tenant.getOrganizationId());
      Instant expiry = life.getExpiresAt();
      created += ensureReminder(tenant.getOrganizationId(), opp.getId(), "T30", expiry.minus(30, ChronoUnit.DAYS), expiry);
      created += ensureReminder(tenant.getOrganizationId(), opp.getId(), "T14", expiry.minus(14, ChronoUnit.DAYS), expiry);
      created += ensureReminder(tenant.getOrganizationId(), opp.getId(), "T7", expiry.minus(7, ChronoUnit.DAYS), expiry);
      if ("GRACE".equalsIgnoreCase(life.getStatus()) && life.getGraceEndsAt() != null) {
        created +=
            ensureReminder(
                tenant.getOrganizationId(),
                opp.getId(),
                "GRACE",
                now,
                life.getGraceEndsAt());
      }
    }

    Instant ago30 = now.minus(30, ChronoUnit.DAYS);
    Map<String, Long> failedByOrg =
        paymentRepository.findAll().stream()
            .filter(p -> "FAILED".equalsIgnoreCase(p.getStatus()))
            .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(ago30))
            .collect(Collectors.groupingBy(SubscriptionPaymentEntity::getOrganizationId, Collectors.counting()));
    for (Map.Entry<String, Long> e : failedByOrg.entrySet()) {
      if (e.getValue() < 1) {
        continue;
      }
      RenewalOpportunityEntity opp = upsertOpportunity(e.getKey());
      created +=
          ensureReminder(
              e.getKey(),
              opp.getId(),
              "FAIL_PAY",
              now,
              now.plus(1, ChronoUnit.DAYS));
    }
    return created;
  }

  private int ensureReminder(
      String organizationId, Long opportunityId, String type, Instant dueAt, Instant expiry) {
    if (dueAt == null) {
      return 0;
    }
    // Normalize due_at to start-of-day-ish uniqueness: truncate to minutes
    Instant due = dueAt.truncatedTo(ChronoUnit.MINUTES);
    if (reminderRepository
        .findByOrganizationIdAndReminderTypeAndDueAtAndStatusIgnoreCase(
            organizationId, type, due, "PENDING")
        .isPresent()) {
      return 0;
    }
    // Also skip if already sent same type for same due
    RenewalReminderEntity row = new RenewalReminderEntity();
    row.setOrganizationId(organizationId);
    row.setOpportunityId(opportunityId);
    row.setReminderType(type);
    row.setDueAt(due);
    row.setStatus("PENDING");
    row.setChannel("IN_APP");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("expiresAt", expiry == null ? null : expiry.toString());
    payload.put("type", type);
    row.setPayloadJson(payload);
    row.setCreatedAt(Instant.now());
    try {
      reminderRepository.save(row);
      return 1;
    } catch (Exception ex) {
      return 0;
    }
  }

  private static Long resolveCap(Map<String, Long> limits, String limitCode) {
    if (limits == null || limitCode == null) {
      return null;
    }
    if (limits.containsKey(limitCode)) {
      return limits.get(limitCode);
    }
    // common aliases: students -> maxStudents
    String camel = "max" + Character.toUpperCase(limitCode.charAt(0)) + limitCode.substring(1);
    if (limits.containsKey(camel)) {
      return limits.get(camel);
    }
    for (Map.Entry<String, Long> e : limits.entrySet()) {
      if (e.getKey().equalsIgnoreCase(limitCode)
          || e.getKey().toLowerCase(Locale.ROOT).contains(limitCode.toLowerCase(Locale.ROOT))) {
        return e.getValue();
      }
    }
    return null;
  }

  private static Map<String, Object> factor(String code, Object value, int delta) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code);
    m.put("value", value);
    m.put("delta", delta);
    return m;
  }

  private Map<String, Object> opportunityToMap(RenewalOpportunityEntity o) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", o.getId());
    map.put("organizationId", o.getOrganizationId());
    map.put("planId", o.getPlanId());
    map.put("stage", o.getStage());
    map.put("healthScore", o.getHealthScore());
    map.put("owner", o.getOwner());
    map.put("nextActionAt", o.getNextActionAt());
    map.put("notes", o.getNotes());
    map.put("updatedAt", o.getUpdatedAt());
    return map;
  }

  private Map<String, Object> reminderToMap(RenewalReminderEntity r) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", r.getId());
    map.put("organizationId", r.getOrganizationId());
    map.put("opportunityId", r.getOpportunityId());
    map.put("reminderType", r.getReminderType());
    map.put("dueAt", r.getDueAt());
    map.put("status", r.getStatus());
    map.put("channel", r.getChannel());
    map.put("payload", r.getPayloadJson());
    map.put("sentAt", r.getSentAt());
    map.put("createdAt", r.getCreatedAt());
    return map;
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v).trim();
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
