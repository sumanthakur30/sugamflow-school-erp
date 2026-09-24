package com.sugamflow.school.student.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GuardianPrimaryParentFilterTest {

  @Test
  void acceptsFatherAndMotherRelations() {
    assertTrue(StudentRecordService.isFatherOrMotherPrimary(Map.of("relation", "Father", "mobile", "1")));
    assertTrue(StudentRecordService.isFatherOrMotherPrimary(Map.of("relation", "Mother", "mobile", "1")));
    assertTrue(StudentRecordService.isFatherOrMotherPrimary(Map.of("relation", "Dad", "mobile", "1")));
    assertTrue(StudentRecordService.isFatherOrMotherPrimary(Map.of("relation", "Mom", "mobile", "1")));
  }

  @Test
  void acceptsPrimaryWithEmptyRelation() {
    assertTrue(
        StudentRecordService.isFatherOrMotherPrimary(
            Map.of("isPrimary", true, "fullName", "Parent", "mobile", "1")));
  }

  @Test
  void rejectsOtherGuardians() {
    assertFalse(
        StudentRecordService.isFatherOrMotherPrimary(
            Map.of("relation", "Uncle", "isPrimary", true, "mobile", "1")));
    assertFalse(
        StudentRecordService.isFatherOrMotherPrimary(
            Map.of("relation", "Neighbour", "mobile", "1")));
    assertFalse(StudentRecordService.isFatherOrMotherPrimary(Map.of("fullName", "No relation")));
  }
}
