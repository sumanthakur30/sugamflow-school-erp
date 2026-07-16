package com.sugamflow.school.common.tenant;

import java.util.Optional;

/**
 * Request-scoped tenancy for multi-org / multi-branch / session configuration.
 * Never hardcode school behavior — resolve via this context + config engines.
 */
public final class TenantContext {

  private static final ThreadLocal<TenantScope> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(TenantScope scope) {
    CURRENT.set(scope);
  }

  public static Optional<TenantScope> get() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static TenantScope require() {
    TenantScope scope = CURRENT.get();
    if (scope == null || scope.organizationId() == null || scope.organizationId().isBlank()) {
      throw new IllegalStateException("Missing tenant context (X-Tenant-Id)");
    }
    return scope;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
