package com.sugamflow.school.transport.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CampusRoutesTest {

  @Test
  void aCampusSeesOnlyItsOwnRoutes() {
    assertTrue(CampusRoutes.visible("campus-a", "campus-a"));
    assertFalse(CampusRoutes.visible("campus-a", "campus-b"));
    assertTrue(CampusRoutes.visible(null, "campus-b"));
  }
}
