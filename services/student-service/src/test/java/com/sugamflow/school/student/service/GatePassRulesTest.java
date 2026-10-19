package com.sugamflow.school.student.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.student.web.StudentException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GatePassRulesTest {

  @Test
  void studentCannotReturnBeforeExit() {
    StudentException ex =
        assertThrows(
            StudentException.class, () -> GatePassRules.nextStatus("STUDENT", "APPROVED", "RETURN", true));
    assertEquals("Record return only after the student has exited", ex.getMessage());
  }

  @Test
  void studentExitThenReturn() {
    assertEquals("OUT", GatePassRules.nextStatus("STUDENT", "APPROVED", "EXIT", false));
    assertEquals("RETURNED", GatePassRules.nextStatus("STUDENT", "OUT", "RETURN", false));
  }

  @Test
  void approvalRequiresAnElevatedRole() {
    assertThrows(
        StudentException.class, () -> GatePassRules.nextStatus("STUDENT", "PENDING", "APPROVE", false));
    assertEquals("APPROVED", GatePassRules.nextStatus("STUDENT", "PENDING", "APPROVE", true));
    assertEquals("REJECTED", GatePassRules.nextStatus("STUDENT", "PENDING", "REJECT", true));
  }

  @Test
  void visitorCheckoutOnlyFromInside() {
    assertEquals("COMPLETED", GatePassRules.nextStatus("VISITOR", "INSIDE", "CHECK_OUT", false));
    assertThrows(
        StudentException.class, () -> GatePassRules.nextStatus("VISITOR", "COMPLETED", "CHECK_OUT", true));
  }

  @Test
  void visitorIsNotForcedThroughStudentActions() {
    assertThrows(
        StudentException.class, () -> GatePassRules.nextStatus("VISITOR", "INSIDE", "EXIT", true));
  }

  @Test
  void pendingPassIsUsedOnlyWhenApprovalWasRequested() {
    assertEquals("APPROVED", GatePassRules.createStatus("STUDENT", false, false));
    assertEquals("PENDING", GatePassRules.createStatus("STUDENT", true, false));
    assertEquals("APPROVED", GatePassRules.createStatus("STUDENT", true, true));
    assertEquals("INSIDE", GatePassRules.createStatus("VISITOR", true, false));
  }

  @Test
  void filtersCombineTypeClassSectionDateAndSearch() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("passType", "STUDENT");
    payload.put("className", "Class 1");
    payload.put("section", "A");
    payload.put("purpose", "Medical");
    payload.put("admissionNo", "1234567891");
    payload.put("passNumber", "GP-20260511-0001");
    payload.put("passDate", "2026-05-11");
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("subjectType", "STUDENT");
    row.put("subjectName", "Firoz Khan");
    row.put("status", "OUT");
    row.put("title", "GP-20260511-0001");
    row.put("payload", payload);

    Map<String, String> query = new LinkedHashMap<>();
    query.put("passType", "STUDENT");
    query.put("className", "class 1");
    query.put("section", "A");
    query.put("purpose", "med");
    query.put("status", "OUT");
    query.put("from", "2026-05-01");
    query.put("to", "2026-05-31");
    query.put("q", "firoz");
    assertTrue(GatePassRules.matches(row, query));

    query.put("section", "B");
    assertFalse(GatePassRules.matches(row, query));
  }
}
