package com.sugamflow.school.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoticeDraftTest {

  @Test
  void aHindiDraftCanBeEditedAndDoesNotCarryTheStudentTable() {
    Map<String, Object> request =
        Map.of(
            "language", "hi",
            "occasion", "सोमवार",
            "students", List.of(Map.of("name", "Asha Kumar", "admissionNo", "ADM-1")));
    Map<String, Object> draft =
        new CommsHubService(null, null)
            .noticeDraft(request);

    assertEquals("hi", draft.get("language"));
    assertTrue(String.valueOf(draft.get("body")).contains("सोमवार"));
    assertFalse(draft.containsKey("students"));
    assertFalse(String.valueOf(draft.get("body")).contains("Asha Kumar"));
    assertFalse(String.valueOf(draft.get("body")).contains("ADM-1"));
  }

  @Test
  void anEnglishDraftUsesTheSelectedLanguage() {
    Map<String, Object> draft = NoticeDraft.write("en", "Monday");

    assertEquals("en", draft.get("language"));
    assertTrue(String.valueOf(draft.get("body")).contains("Monday"));
  }
}
