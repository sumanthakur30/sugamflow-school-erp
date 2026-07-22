package com.sugamflow.school.common.security;

import com.sugamflow.school.common.tenant.TenantScope;
import java.util.Locale;
import java.util.Objects;

/**
 * Multi-campus access helpers. Branch is an authorization boundary: callers must present a branch,
 * elevated roles may manage campuses, and entity branch must match the active campus unless the
 * caller is explicitly elevated for org-wide work.
 */
public final class BranchAccess {

  private BranchAccess() {}

  public static String requireBranchId(TenantScope scope) {
    if (scope == null || scope.branchId() == null || scope.branchId().isBlank()) {
      throw new SecurityException("X-Branch-Id is required for campus-scoped operations");
    }
    return scope.branchId().trim();
  }

  public static void requireElevatedBranchAdmin(TenantScope scope) {
    if (scope == null || !PersonaRoles.isElevated(scope.roleCode())) {
      String role = scope == null ? "" : scope.roleCode();
      throw new SecurityException("Campus administration requires an elevated role: " + role);
    }
  }

  public static boolean canManageBranches(TenantScope scope) {
    return scope != null && PersonaRoles.isElevated(scope.roleCode());
  }

  public static boolean canAccessAllBranches(TenantScope scope) {
    return canManageBranches(scope);
  }

  public static void requireEntityBranch(TenantScope scope, String entityBranchId) {
    if (canAccessAllBranches(scope)) {
      return;
    }
    String active = requireBranchId(scope);
    String entity = entityBranchId == null ? "" : entityBranchId.trim();
    if (entity.isBlank()) {
      throw new SecurityException("Record is missing campus assignment");
    }
    if (!active.equalsIgnoreCase(entity)) {
      throw new SecurityException(
          "Campus mismatch: active=" + active + " record=" + entity);
    }
  }

  public static boolean sameBranch(String left, String right) {
    String a = left == null ? "" : left.trim().toLowerCase(Locale.ROOT);
    String b = right == null ? "" : right.trim().toLowerCase(Locale.ROOT);
    return !a.isEmpty() && Objects.equals(a, b);
  }
}
