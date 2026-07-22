package com.sugamflow.school.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.common.tenant.TenantScope;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonaRolesAndAccessScopeTest {

  @Test
  void classifiesPortalRoles() {
    assertTrue(PersonaRoles.isParent("PARENT"));
    assertTrue(PersonaRoles.isTeacher("teacher"));
    assertTrue(PersonaRoles.isElevated("SHOP_OWNER"));
    assertTrue(PersonaRoles.isRelationshipRestricted("PARENT"));
    assertFalse(PersonaRoles.isRelationshipRestricted("ADMIN"));
  }

  @Test
  void parentWriteIsBlocked() {
    assertThrows(
        SecurityException.class,
        () ->
            PersonaRoles.requireStaffWrite(
                new TenantScope("NAT-01", "main", "2025-26", "p1", "PARENT")));
  }

  @Test
  void accessScopeMatchesAnswersByAdmissionOrClass() {
    AccessScope scope = AccessScope.of("PARENT", Set.of(), Set.of("ADM-9"), Set.of("8-a"));
    assertTrue(scope.allowsAnswers(Map.of("admissionNo", "ADM-9")));
    assertTrue(scope.allowsAnswers(Map.of("classSection", "8-A")));
    assertFalse(scope.allowsAnswers(Map.of("admissionNo", "ADM-1")));
    assertTrue(AccessScope.elevated().allowsAnswers(Map.of("admissionNo", "anything")));
  }
}
