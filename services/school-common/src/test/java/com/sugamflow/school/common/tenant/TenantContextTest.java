package com.sugamflow.school.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void requireThrowsWhenNoScopeBound() {
    assertThrows(IllegalStateException.class, TenantContext::require);
  }

  @Test
  void requireThrowsWhenOrganizationBlank() {
    TenantContext.set(new TenantScope("", "main", "2025-26", "u1", "ADMIN"));
    assertThrows(IllegalStateException.class, TenantContext::require);
  }

  @Test
  void requireReturnsBoundScope() {
    TenantContext.set(new TenantScope("NAT-01", "main", "2025-26", "u1", "SHOP_OWNER"));
    TenantScope scope = TenantContext.require();
    assertEquals("NAT-01", scope.organizationId());
    assertEquals("main", scope.branchId());
    assertEquals("2025-26", scope.academicSessionId());
  }

  @Test
  void clearRemovesScope() {
    TenantContext.set(new TenantScope("NAT-01", null, null, null, null));
    TenantContext.clear();
    assertTrue(TenantContext.get().isEmpty());
  }
}
