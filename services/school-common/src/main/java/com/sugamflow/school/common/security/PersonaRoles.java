package com.sugamflow.school.common.security;

import com.sugamflow.school.common.tenant.TenantScope;
import java.util.Locale;
import java.util.Set;

/** Portal vs staff role classification for relationship-scoped APIs. */
public final class PersonaRoles {

  public static final Set<String> ELEVATED =
      Set.of("SHOP_OWNER", "SUPER_ADMIN", "ADMIN", "PRINCIPAL");

  private PersonaRoles() {}

  public static String normalize(String roleCode) {
    return roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
  }

  public static boolean isElevated(String roleCode) {
    return ELEVATED.contains(normalize(roleCode));
  }

  public static boolean isParent(String roleCode) {
    return "PARENT".equals(normalize(roleCode)) || "GUARDIAN".equals(normalize(roleCode));
  }

  public static boolean isTeacher(String roleCode) {
    return "TEACHER".equals(normalize(roleCode));
  }

  public static boolean isStudent(String roleCode) {
    return "STUDENT".equals(normalize(roleCode));
  }

  /** Personas that must never see org-wide student/fee/attendance/exam lists. */
  public static boolean isRelationshipRestricted(String roleCode) {
    return isParent(roleCode) || isTeacher(roleCode) || isStudent(roleCode);
  }

  /** Parents/students are read-only on operational write APIs. */
  public static boolean isPortalReadOnly(String roleCode) {
    return isParent(roleCode) || isStudent(roleCode);
  }

  public static void requireStaffWrite(TenantScope scope) {
    if (scope != null && isPortalReadOnly(scope.roleCode())) {
      throw new SecurityException("Write actions are not allowed for portal role: " + scope.roleCode());
    }
  }
}
