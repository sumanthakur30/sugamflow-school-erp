package com.sugamflow.school.cms.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CmsSiteScopeTest {

  @Test
  void legacyScopeShowsOnlyNullSiteRows() {
    CmsSiteScope scope = CmsSiteScope.legacy();
    assertTrue(scope.matches(null));
    assertFalse(scope.matches(UUID.randomUUID()));
  }

  @Test
  void defaultSiteIncludesLegacyAndOwnRows() {
    UUID site = UUID.randomUUID();
    CmsSiteScope scope = CmsSiteScope.parse(site.toString(), true);
    assertTrue(scope.matches(null));
    assertTrue(scope.matches(site));
    assertFalse(scope.matches(UUID.randomUUID()));
  }

  @Test
  void campusSiteDoesNotIncludeLegacyRows() {
    UUID site = UUID.randomUUID();
    CmsSiteScope scope = CmsSiteScope.parse(site.toString(), false);
    assertFalse(scope.matches(null));
    assertTrue(scope.matches(site));
    assertFalse(scope.matches(UUID.randomUUID()));
  }
}
