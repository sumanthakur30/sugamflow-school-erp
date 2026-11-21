package com.sugamflow.school.settings.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortalCatalogTest {

  @Test
  void studentPortalReusesTheParentPlanAndHasNoFeePage() {
    assertTrue(PortalCatalog.isKnown("student"));
    assertEquals("student_portal", PortalCatalog.moduleKey("student"));
    assertEquals("FEATURE_PARENT_APP", PortalCatalog.featureFlag("student"));
    assertEquals("FEATURE_TEACHER_APP", PortalCatalog.featureFlag("teacher"));

    Map<String, Object> settings = PortalCatalog.defaultStudentSettings();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> nav = (List<Map<String, Object>>) settings.get("nav");
    assertTrue(nav.stream().anyMatch(item -> "/student/attendance".equals(item.get("route"))));
    assertFalse(nav.stream().anyMatch(item -> "fees".equals(item.get("id"))));
    assertFalse(
        nav.stream().anyMatch(item -> String.valueOf(item.get("route")).contains("/fees")));
  }
}
