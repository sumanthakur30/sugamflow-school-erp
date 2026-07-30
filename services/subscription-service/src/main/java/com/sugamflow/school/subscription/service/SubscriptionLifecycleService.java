package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionLifecycleEntity;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionLifecycleRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Additive tenant license lifecycle. Defaults to ACTIVE forever with enforcement off so School
 * entitlements/flags are unchanged until an admin configures dates and enables enforcement.
 */
@Service
public class SubscriptionLifecycleService {

  public static final String STATUS_ACTIVE = "ACTIVE";
  public static final String STATUS_TRIAL = "TRIAL";
  public static final String STATUS_GRACE = "GRACE";
  public static final String STATUS_EXPIRED = "EXPIRED";
  public static final String STATUS_SUSPENDED = "SUSPENDED";
  public static final String STATUS_CANCELLED = "CANCELLED";

  private static final Set<String> ACCESS_OK =
      Set.of(STATUS_ACTIVE, STATUS_TRIAL, STATUS_GRACE);

  private final TenantSubscriptionLifecycleRepository lifecycleRepository;
  private final TenantSubscriptionRepository tenantSubscriptionRepository;

  public SubscriptionLifecycleService(
      TenantSubscriptionLifecycleRepository lifecycleRepository,
      TenantSubscriptionRepository tenantSubscriptionRepository) {
    this.lifecycleRepository = lifecycleRepository;
    this.tenantSubscriptionRepository = tenantSubscriptionRepository;
  }

  /** Ensure 1:1 lifecycle row after plan assign (ACTIVE forever, enforcement off). */
  @Transactional
  public TenantSubscriptionLifecycleEntity ensureActiveForever(String organizationId) {
    return lifecycleRepository
        .findById(organizationId)
        .orElseGet(
            () -> {
              TenantSubscriptionLifecycleEntity row = newActiveForever(organizationId);
              return lifecycleRepository.save(row);
            });
  }

  @Transactional
  public int backfillMissing() {
    int created = 0;
    for (TenantSubscriptionEntity tenant : tenantSubscriptionRepository.findAll()) {
      if (!lifecycleRepository.existsById(tenant.getOrganizationId())) {
        lifecycleRepository.save(newActiveForever(tenant.getOrganizationId()));
        created++;
      }
    }
    return created;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getLicense(String organizationId) {
    String planId =
        tenantSubscriptionRepository
            .findById(organizationId)
            .map(TenantSubscriptionEntity::getPlanId)
            .orElse("starter");

    TenantSubscriptionLifecycleEntity row =
        lifecycleRepository.findById(organizationId).orElse(null);
    Instant now = Instant.now();
    String resolved =
        row == null ? STATUS_ACTIVE : resolveStatus(row, now, false);
    boolean enforcement = row != null && row.isEnforcementEnabled();
    boolean accessAllowed = !enforcement || ACCESS_OK.contains(resolved);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("planId", planId);
    out.put("resolvedStatus", resolved);
    out.put("enforcementEnabled", enforcement);
    out.put("accessAllowed", accessAllowed);
    out.put("sourceOfTruthEntitlements", "subscription_plan JSON (unchanged by lifecycle)");
    if (row == null) {
      out.put("status", STATUS_ACTIVE);
      out.put("trialEndsAt", null);
      out.put("expiresAt", null);
      out.put("graceEndsAt", null);
      out.put("graceDays", 7);
      out.put("notes", "virtual ACTIVE forever (no lifecycle row yet)");
      return out;
    }
    out.put("status", row.getStatus());
    out.put("trialEndsAt", row.getTrialEndsAt());
    out.put("expiresAt", row.getExpiresAt());
    out.put("graceEndsAt", row.getGraceEndsAt());
    out.put("graceDays", row.getGraceDays());
    out.put("notes", row.getNotes());
    out.put("updatedAt", row.getUpdatedAt());
    return out;
  }

  @Transactional
  public Map<String, Object> updateLicense(String organizationId, Map<String, Object> body) {
    requireTenantAssignment(organizationId);
    TenantSubscriptionLifecycleEntity row =
        lifecycleRepository
            .findById(organizationId)
            .orElseGet(() -> newActiveForever(organizationId));

    if (body.containsKey("status") && body.get("status") != null) {
      row.setStatus(normalizeStatus(String.valueOf(body.get("status"))));
    }
    if (body.containsKey("trialEndsAt")) {
      row.setTrialEndsAt(parseInstant(body.get("trialEndsAt")));
    }
    if (body.containsKey("expiresAt")) {
      row.setExpiresAt(parseInstant(body.get("expiresAt")));
    }
    if (body.containsKey("graceEndsAt")) {
      row.setGraceEndsAt(parseInstant(body.get("graceEndsAt")));
    }
    if (body.containsKey("graceDays") && body.get("graceDays") != null) {
      row.setGraceDays(Integer.parseInt(String.valueOf(body.get("graceDays"))));
    }
    if (body.containsKey("enforcementEnabled") && body.get("enforcementEnabled") != null) {
      row.setEnforcementEnabled(Boolean.parseBoolean(String.valueOf(body.get("enforcementEnabled"))));
    }
    if (body.containsKey("notes")) {
      Object notes = body.get("notes");
      row.setNotes(notes == null ? null : String.valueOf(notes));
    }
    row.setUpdatedAt(Instant.now());
    String resolved = resolveStatus(row, Instant.now(), true);
    row.setStatus(resolved);
    lifecycleRepository.save(row);
    return getLicense(organizationId);
  }

  @Transactional
  public Map<String, Object> renew(String organizationId, Map<String, Object> body) {
    requireTenantAssignment(organizationId);
    TenantSubscriptionLifecycleEntity row =
        lifecycleRepository
            .findById(organizationId)
            .orElseGet(() -> newActiveForever(organizationId));

    Instant expiresAt = parseInstant(body != null ? body.get("expiresAt") : null);
    if (expiresAt == null && body != null && body.get("days") != null) {
      long days = Long.parseLong(String.valueOf(body.get("days")));
      expiresAt = Instant.now().plus(days, ChronoUnit.DAYS);
    }
    row.setExpiresAt(expiresAt);
    row.setGraceEndsAt(null);
    row.setStatus(STATUS_ACTIVE);
    if (body != null && body.containsKey("enforcementEnabled") && body.get("enforcementEnabled") != null) {
      row.setEnforcementEnabled(Boolean.parseBoolean(String.valueOf(body.get("enforcementEnabled"))));
    }
    if (body != null && body.get("notes") != null) {
      row.setNotes(String.valueOf(body.get("notes")));
    }
    row.setUpdatedAt(Instant.now());
    lifecycleRepository.save(row);
    return getLicense(organizationId);
  }

  @Transactional
  public Map<String, Object> suspend(String organizationId, String notes) {
    return setTerminal(organizationId, STATUS_SUSPENDED, notes);
  }

  @Transactional
  public Map<String, Object> cancel(String organizationId, String notes) {
    return setTerminal(organizationId, STATUS_CANCELLED, notes);
  }

  @Transactional
  public Map<String, Object> resume(String organizationId) {
    requireTenantAssignment(organizationId);
    TenantSubscriptionLifecycleEntity row =
        lifecycleRepository
            .findById(organizationId)
            .orElseGet(() -> newActiveForever(organizationId));
    row.setStatus(STATUS_ACTIVE);
    row.setUpdatedAt(Instant.now());
    String resolved = resolveStatus(row, Instant.now(), true);
    row.setStatus(resolved);
    lifecycleRepository.save(row);
    return getLicense(organizationId);
  }

  String resolveStatus(TenantSubscriptionLifecycleEntity row, Instant now, boolean persistGraceEnd) {
    if (row == null) {
      return STATUS_ACTIVE;
    }
    String stored = row.getStatus() == null ? STATUS_ACTIVE : normalizeStatus(row.getStatus());
    if (STATUS_CANCELLED.equals(stored) || STATUS_SUSPENDED.equals(stored)) {
      return stored;
    }
    if (row.getTrialEndsAt() != null && !now.isAfter(row.getTrialEndsAt())) {
      return STATUS_TRIAL;
    }
    Instant expiry = row.getExpiresAt();
    if (expiry == null || !now.isAfter(expiry)) {
      return STATUS_ACTIVE;
    }
    Instant graceEnd = row.getGraceEndsAt();
    if (graceEnd == null) {
      int days = row.getGraceDays() <= 0 ? 7 : row.getGraceDays();
      graceEnd = expiry.plus(days, ChronoUnit.DAYS);
      if (persistGraceEnd) {
        row.setGraceEndsAt(graceEnd);
      }
    }
    if (!now.isAfter(graceEnd)) {
      return STATUS_GRACE;
    }
    return STATUS_EXPIRED;
  }

  private Map<String, Object> setTerminal(String organizationId, String status, String notes) {
    requireTenantAssignment(organizationId);
    TenantSubscriptionLifecycleEntity row =
        lifecycleRepository
            .findById(organizationId)
            .orElseGet(() -> newActiveForever(organizationId));
    row.setStatus(status);
    if (notes != null && !notes.isBlank()) {
      row.setNotes(notes.trim());
    }
    row.setUpdatedAt(Instant.now());
    lifecycleRepository.save(row);
    return getLicense(organizationId);
  }

  private void requireTenantAssignment(String organizationId) {
    if (!tenantSubscriptionRepository.existsById(organizationId)) {
      throw new IllegalArgumentException(
          "No tenant subscription for organization: " + organizationId + " — assign a plan first");
    }
  }

  private static TenantSubscriptionLifecycleEntity newActiveForever(String organizationId) {
    TenantSubscriptionLifecycleEntity row = new TenantSubscriptionLifecycleEntity();
    row.setOrganizationId(organizationId);
    row.setStatus(STATUS_ACTIVE);
    row.setTrialEndsAt(null);
    row.setExpiresAt(null);
    row.setGraceEndsAt(null);
    row.setGraceDays(7);
    row.setEnforcementEnabled(false);
    row.setNotes("ACTIVE forever (enforcement off)");
    Instant now = Instant.now();
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    return row;
  }

  private static String normalizeStatus(String status) {
    return status.trim().toUpperCase(Locale.ROOT);
  }

  private static Instant parseInstant(Object raw) {
    if (raw == null) {
      return null;
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
      return null;
    }
    return Instant.parse(s);
  }
}
