package com.sugamflow.school.settings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.config.SettingsProperties;
import com.sugamflow.school.settings.integration.AuditClient;
import com.sugamflow.school.settings.integration.SubscriptionClient;
import com.sugamflow.school.settings.model.DesignTheme;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchoolProvisionerServiceTest {

  private static final String ORG = "NAT-01";

  @Mock private BranchRegistryService branches;
  @Mock private SettingsConfigService settings;
  @Mock private SubscriptionClient subscription;
  @Mock private AuditClient auditClient;

  private SchoolProvisionerService service;

  @BeforeEach
  void setUp() {
    SettingsProperties properties = new SettingsProperties();
    // Point shop lookup at a dead port so it fails fast and returns null.
    properties.getIntegrations().setPublicApiBaseUrl("");
    service =
        new SchoolProvisionerService(
            branches, settings, subscription, auditClient, properties, RestClient.builder());
    TenantContext.set(new TenantScope(ORG, "main", "2025-26", "user-1", "SHOP_OWNER"));

    when(branches.ensureMainCampus(ORG))
        .thenReturn(Map.of("branchKey", "main", "name", "Main Campus"));
    when(settings.getOrCreateTheme(ORG, "main"))
        .thenReturn(DesignTheme.platformDefault(ORG, "main"));
    when(settings.saveTheme(any(DesignTheme.class))).thenAnswer(inv -> inv.getArgument(0));
    when(subscription.getEntitlements(any())).thenReturn(Map.of("planId", "starter"));
    when(subscription.assignPlan(any(), eq("starter")))
        .thenReturn(Map.of("planId", "starter", "organizationId", ORG));
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void firstProvisionSetsSchoolNameAndMarksProvisioned() {
    Map<String, Object> out = service.provision(Map.of("schoolName", "NAT Public School"));

    assertEquals(ORG, out.get("organizationId"));
    assertEquals(true, out.get("provisioned"));
    assertEquals("NAT Public School", out.get("schoolName"));
    assertNotNull(out.get("branch"));
    verify(branches).ensureMainCampus(ORG);
    verify(settings).saveTheme(any(DesignTheme.class));
  }

  @Test
  void doesNotOverwriteCustomSchoolName() {
    DesignTheme customized = DesignTheme.platformDefault(ORG, "main");
    customized.getBranding().put("schoolName", "St. Mary Convent");
    customized.getBranding().put("provisionedAt", "2026-01-01T00:00:00Z");
    when(settings.getOrCreateTheme(ORG, "main")).thenReturn(customized);

    Map<String, Object> out = service.provision(Map.of("schoolName", "NAT Public School"));

    assertEquals("St. Mary Convent", out.get("schoolName"));
    assertEquals(true, out.get("alreadyProvisioned"));
    verify(settings, never()).saveTheme(any(DesignTheme.class));
  }

  @Test
  void secondProvisionIsIdempotent() {
    DesignTheme provisioned = DesignTheme.platformDefault(ORG, "main");
    provisioned.getBranding().put("schoolName", "NAT Public School");
    provisioned.getBranding().put("provisionedAt", "2026-07-17T05:35:27Z");
    when(settings.getOrCreateTheme(ORG, "main")).thenReturn(provisioned);

    Map<String, Object> out = service.provision(Map.of("schoolName", "NAT Public School"));

    assertEquals(true, out.get("alreadyProvisioned"));
    verify(settings, never()).saveTheme(any(DesignTheme.class));
  }

  @Test
  void provisionWithoutBodyStillEnsuresCampusAndMarker() {
    Map<String, Object> out = service.provision(Map.of());

    assertEquals(true, out.get("provisioned"));
    verify(branches).ensureMainCampus(ORG);
    // No shop name resolvable -> keeps platform default, but marker is written.
    verify(settings).saveTheme(any(DesignTheme.class));
    assertTrue(String.valueOf(out.get("schoolName")).length() > 0);
  }

  @Test
  void provisionRequiresTenantContext() {
    TenantContext.clear();
    assertThrows(IllegalStateException.class, () -> service.provision(Map.of()));
  }

  @Test
  void assignsStarterPlanWhenTenantRowMissing() {
    service.provision(Map.of("schoolName", "NAT Public School"));
    verify(subscription).assignPlan(any(), eq("starter"));
  }

  @Test
  void keepsPaidPlanUntouched() {
    when(subscription.getEntitlements(any())).thenReturn(Map.of("planId", "premium"));

    Map<String, Object> out = service.provision(Map.of("schoolName", "NAT Public School"));

    verify(subscription, never()).assignPlan(any(), anyString());
    assertEquals("premium", out.get("planId"));
  }
}
