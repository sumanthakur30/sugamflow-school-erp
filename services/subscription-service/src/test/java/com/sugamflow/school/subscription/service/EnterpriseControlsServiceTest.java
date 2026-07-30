package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.persistence.entity.EnterpriseAuditEventEntity;
import com.sugamflow.school.subscription.persistence.entity.EnterpriseAuditExportEntity;
import com.sugamflow.school.subscription.persistence.entity.EnterpriseOrgSettingsEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.repo.EnterpriseAuditEventRepository;
import com.sugamflow.school.subscription.persistence.repo.EnterpriseAuditExportRepository;
import com.sugamflow.school.subscription.persistence.repo.EnterpriseOrgSettingsRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnterpriseControlsServiceTest {

  @Mock private EnterpriseOrgSettingsRepository settingsRepository;
  @Mock private EnterpriseAuditEventRepository auditEventRepository;
  @Mock private EnterpriseAuditExportRepository exportRepository;
  @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
  @Mock private SubscriptionService subscriptionService;

  private EnterpriseControlsService service;

  @BeforeEach
  void setUp() {
    service =
        new EnterpriseControlsService(
            settingsRepository,
            auditEventRepository,
            exportRepository,
            tenantSubscriptionRepository,
            subscriptionService,
            new ObjectMapper());
  }

  @Test
  void upsertSettingsRejectsSsoWithoutEntitlement() {
    stubPlan("ORG1", "starter", Map.of("FEATURE_WHITE_LABEL", false));
    when(settingsRepository.findById("ORG1")).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> service.upsertSettings("ORG1", Map.of("ssoEnabled", true, "ssoProvider", "OIDC")));
  }

  @Test
  void upsertSettingsPersistsWhenWhiteLabelEntitled() {
    stubPlan("ORG1", "enterprise", Map.of("FEATURE_WHITE_LABEL", true));
    when(settingsRepository.findById("ORG1")).thenReturn(Optional.empty());
    when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(auditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> out =
        service.upsertSettings(
            "ORG1",
            Map.of(
                "ssoEnabled",
                true,
                "ssoProvider",
                "OIDC",
                "whiteLabelEnabled",
                true,
                "whiteLabel",
                Map.of("logoUrl", "https://cdn.example/logo.png"),
                "residencyRegion",
                "IN",
                "actor",
                "qa"));

    assertTrue(Boolean.TRUE.equals(out.get("ssoEnabled")));
    assertEquals("OIDC", out.get("ssoProvider"));
    assertTrue(Boolean.TRUE.equals(out.get("whiteLabelEnabled")));
    assertEquals("IN", out.get("residencyRegion"));
    @SuppressWarnings("unchecked")
    Map<String, Boolean> entitled = (Map<String, Boolean>) out.get("entitlements");
    assertTrue(entitled.get("sso"));
    assertTrue(entitled.get("whiteLabel"));
    verify(auditEventRepository).save(any(EnterpriseAuditEventEntity.class));
  }

  @Test
  void createAuditExportBuildsCsv() {
    EnterpriseAuditEventEntity event = new EnterpriseAuditEventEntity();
    event.setId(1L);
    event.setOrganizationId("ORG1");
    event.setEventType("ENTERPRISE_SETTINGS_UPDATED");
    event.setActor("qa");
    event.setDetailJson(Map.of("k", "v"));
    when(auditEventRepository.findForExport("ORG1", null, null)).thenReturn(List.of(event));
    AtomicLong ids = new AtomicLong(10);
    when(exportRepository.save(any()))
        .thenAnswer(
            inv -> {
              EnterpriseAuditExportEntity e = inv.getArgument(0);
              e.setId(ids.getAndIncrement());
              return e;
            });
    when(auditEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> export =
        service.createAuditExport("ORG1", Map.of("format", "CSV", "requestedBy", "qa"));

    assertEquals("READY", export.get("status"));
    assertEquals(1, export.get("rowCount"));
    assertTrue(String.valueOf(export.get("content")).contains("ENTERPRISE_SETTINGS_UPDATED"));
  }

  @Test
  void getSettingsReturnsDefaultsWhenMissing() {
    stubPlan("ORG1", "starter", Map.of());
    when(settingsRepository.findById("ORG1")).thenReturn(Optional.empty());

    Map<String, Object> out = service.getSettings("ORG1");
    assertEquals("ORG1", out.get("organizationId"));
    assertFalse(Boolean.TRUE.equals(out.get("ssoEnabled")));
    assertFalse(Boolean.TRUE.equals(out.get("whiteLabelEnabled")));
  }

  private void stubPlan(String org, String planId, Map<String, Boolean> flags) {
    TenantSubscriptionEntity t = new TenantSubscriptionEntity();
    t.setOrganizationId(org);
    t.setPlanId(planId);
    when(tenantSubscriptionRepository.findById(org)).thenReturn(Optional.of(t));
    SubscriptionPlan plan = new SubscriptionPlan();
    plan.setId(planId);
    plan.setFeatureFlags(flags);
    when(subscriptionService.getPlan(planId)).thenReturn(plan);
  }
}
