package com.sugamflow.school.student.sheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudentGuardianSyncTest {

  @Test
  void writesFatherAndMotherWithoutRemovingAnotherGuardian() {
    Map<String, Object> answers = new LinkedHashMap<>();
    answers.put("fatherName", "Ravi Rao");
    answers.put("fatherMobile", "9800000002");
    answers.put("motherName", "Meera Rao");
    List<Map<String, Object>> guardians = new ArrayList<>();
    guardians.add(Map.of("fullName", "Nani Rao", "relation", "Guardian", "mobile", "9800000099"));
    answers.put("guardians", guardians);

    StudentGuardianSync.syncFromFlat(answers);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> saved = (List<Map<String, Object>>) answers.get("guardians");
    assertEquals(3, saved.size());
    assertEquals("Nani Rao", saved.get(0).get("fullName"));
    assertEquals("Ravi Rao", saved.get(1).get("fullName"));
    assertEquals("Father", saved.get(1).get("relation"));
    assertEquals("Meera Rao", saved.get(2).get("fullName"));
  }

  @Test
  void blankFlatNamesDoNotEraseAStoredGuardian() {
    Map<String, Object> guardian = new LinkedHashMap<>();
    guardian.put("fullName", "Ravi Rao");
    guardian.put("relation", "Father");
    guardian.put("mobile", "9800000002");
    Map<String, Object> answers = new LinkedHashMap<>();
    answers.put("guardians", List.of(guardian));

    StudentGuardianSync.syncFromFlat(answers);

    assertEquals("Ravi Rao", StudentGuardianSync.nameForRelation(answers, "father"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> saved = (List<Map<String, Object>>) answers.get("guardians");
    assertEquals("9800000002", saved.get(0).get("mobile"));
    assertTrue(String.valueOf(answers.get("fatherName")).equals("Ravi Rao"));
  }
}
