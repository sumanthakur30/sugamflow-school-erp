package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionLifecycleEntity;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionLifecycleRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleServiceTest {

  private static final String ORG = "SCH-01";

  @Mock private TenantSubscriptionLifecycleRepository lifecycleRepository;
  @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;

  private SubscriptionLifecycleService service;

  @BeforeEach
  void setUp() {
    service = new SubscriptionLifecycleService(lifecycleRepository, tenantSubscriptionRepository);
  }

  @Test
  void virtualLicenseIsActiveForeverWhenNoRow() {
    when(tenantSubscriptionRepository.findById(ORG)).thenReturn(Optional.empty());
    when(lifecycleRepository.findById(ORG)).thenReturn(Optional.empty());

    Map<String, Object> license = service.getLicense(ORG);

    assertEquals("starter", license.get("planId"));
    assertEquals("ACTIVE", license.get("resolvedStatus"));
    assertEquals(false, license.get("enforcementEnabled"));
    assertEquals(true, license.get("accessAllowed"));
  }

  @Test
  void resolveStatusTrialBeforeExpiry() {
    TenantSubscriptionLifecycleEntity row = activeForever();
    row.setTrialEndsAt(Instant.now().plus(3, ChronoUnit.DAYS));
    assertEquals("TRIAL", service.resolveStatus(row, Instant.now(), false));
  }

  @Test
  void resolveStatusGraceAfterExpiry() {
    TenantSubscriptionLifecycleEntity row = activeForever();
    Instant expiry = Instant.now().minus(1, ChronoUnit.DAYS);
    row.setExpiresAt(expiry);
    row.setGraceDays(7);
    assertEquals("GRACE", service.resolveStatus(row, Instant.now(), false));
  }

  @Test
  void resolveStatusExpiredAfterGrace() {
    TenantSubscriptionLifecycleEntity row = activeForever();
    Instant expiry = Instant.now().minus(20, ChronoUnit.DAYS);
    row.setExpiresAt(expiry);
    row.setGraceEndsAt(Instant.now().minus(1, ChronoUnit.DAYS));
    assertEquals("EXPIRED", service.resolveStatus(row, Instant.now(), false));
  }

  @Test
  void accessAllowedWhenEnforcementOffEvenIfExpired() {
    when(tenantSubscriptionRepository.findById(ORG))
        .thenReturn(Optional.of(assignment("starter")));
    TenantSubscriptionLifecycleEntity row = activeForever();
    row.setExpiresAt(Instant.now().minus(30, ChronoUnit.DAYS));
    row.setGraceEndsAt(Instant.now().minus(20, ChronoUnit.DAYS));
    row.setStatus("EXPIRED");
    row.setEnforcementEnabled(false);
    when(lifecycleRepository.findById(ORG)).thenReturn(Optional.of(row));

    Map<String, Object> license = service.getLicense(ORG);

    assertEquals("EXPIRED", license.get("resolvedStatus"));
    assertEquals(true, license.get("accessAllowed"));
  }

  @Test
  void accessDeniedWhenEnforcementOnAndExpired() {
    when(tenantSubscriptionRepository.findById(ORG))
        .thenReturn(Optional.of(assignment("starter")));
    TenantSubscriptionLifecycleEntity row = activeForever();
    row.setExpiresAt(Instant.now().minus(30, ChronoUnit.DAYS));
    row.setGraceEndsAt(Instant.now().minus(20, ChronoUnit.DAYS));
    row.setEnforcementEnabled(true);
    when(lifecycleRepository.findById(ORG)).thenReturn(Optional.of(row));

    Map<String, Object> license = service.getLicense(ORG);

    assertEquals("EXPIRED", license.get("resolvedStatus"));
    assertEquals(false, license.get("accessAllowed"));
  }

  @Test
  void renewSetsActiveAndClearsGrace() {
    when(tenantSubscriptionRepository.existsById(ORG)).thenReturn(true);
    TenantSubscriptionLifecycleEntity row = activeForever();
    row.setGraceEndsAt(Instant.now().plus(1, ChronoUnit.DAYS));
    when(lifecycleRepository.findById(ORG)).thenReturn(Optional.of(row));
    when(lifecycleRepository.save(any()))
        .thenAnswer(
            inv -> {
              TenantSubscriptionLifecycleEntity saved = inv.getArgument(0);
              row.setStatus(saved.getStatus());
              row.setExpiresAt(saved.getExpiresAt());
              row.setGraceEndsAt(saved.getGraceEndsAt());
              row.setEnforcementEnabled(saved.isEnforcementEnabled());
              return saved;
            });
    when(tenantSubscriptionRepository.findById(ORG))
        .thenReturn(Optional.of(assignment("enterprise")));

    Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
    Map<String, Object> out =
        service.renew(ORG, Map.of("expiresAt", expiry.toString(), "enforcementEnabled", true));

    assertEquals(null, row.getGraceEndsAt());
    assertEquals("ACTIVE", row.getStatus());
    assertTrue(row.isEnforcementEnabled());
    assertEquals("enterprise", out.get("planId"));
    assertEquals("ACTIVE", out.get("resolvedStatus"));
  }

  @Test
  void updateRequiresPlanAssignment() {
    when(tenantSubscriptionRepository.existsById(ORG)).thenReturn(false);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.updateLicense(ORG, Map.of("expiresAt", Instant.now().toString())));
  }

  @Test
  void backfillMissingCreatesRows() {
    TenantSubscriptionEntity tenant = assignment("starter");
    when(tenantSubscriptionRepository.findAll()).thenReturn(List.of(tenant));
    when(lifecycleRepository.existsById(ORG)).thenReturn(false);
    when(lifecycleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    int created = service.backfillMissing();

    assertEquals(1, created);
    verify(lifecycleRepository).save(any(TenantSubscriptionLifecycleEntity.class));
  }

  @Test
  void suspendIsTerminal() {
    when(tenantSubscriptionRepository.existsById(ORG)).thenReturn(true);
    TenantSubscriptionLifecycleEntity row = activeForever();
    when(lifecycleRepository.findById(ORG)).thenReturn(Optional.of(row));
    when(lifecycleRepository.save(any()))
        .thenAnswer(
            inv -> {
              TenantSubscriptionLifecycleEntity saved = inv.getArgument(0);
              row.setStatus(saved.getStatus());
              row.setNotes(saved.getNotes());
              return saved;
            });
    when(tenantSubscriptionRepository.findById(ORG))
        .thenReturn(Optional.of(assignment("starter")));

    Map<String, Object> out = service.suspend(ORG, "payment failed");

    assertEquals("SUSPENDED", out.get("resolvedStatus"));
    assertEquals("payment failed", out.get("notes"));
  }

  private static TenantSubscriptionLifecycleEntity activeForever() {
    TenantSubscriptionLifecycleEntity row = new TenantSubscriptionLifecycleEntity();
    row.setOrganizationId(ORG);
    row.setStatus("ACTIVE");
    row.setGraceDays(7);
    row.setEnforcementEnabled(false);
    return row;
  }

  private static TenantSubscriptionEntity assignment(String planId) {
    TenantSubscriptionEntity e = new TenantSubscriptionEntity();
    e.setOrganizationId(ORG);
    e.setPlanId(planId);
    return e;
  }
}
