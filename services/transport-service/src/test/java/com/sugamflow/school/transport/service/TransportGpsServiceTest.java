package com.sugamflow.school.transport.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.transport.integration.ConfigEngineClient;
import com.sugamflow.school.transport.persistence.repo.TransportGpsPingRepository;
import com.sugamflow.school.transport.web.TransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransportGpsServiceTest {

  @Mock private TransportGpsPingRepository repository;
  @Mock private ConfigEngineClient engines;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void locationRequiresALogin() {
    TenantContext.clear();
    TransportGpsService service = new TransportGpsService(repository, engines);

    assertThrows(IllegalStateException.class, service::latest);
    verifyNoInteractions(repository);
  }

  @Test
  void locationStaysOffUntilThePilotIsOn() {
    TenantContext.set(new TenantScope("org", "campus-a", "2026", "user", "ADMIN"));
    when(engines.isFeatureEnabled(any(), eq(TransportGpsService.FEATURE_GPS))).thenReturn(false);
    TransportGpsService service = new TransportGpsService(repository, engines);

    assertThrows(TransportException.class, service::latest);
    verifyNoInteractions(repository);
  }
}
