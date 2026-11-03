package com.sugamflow.school.student.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BillableEnrollmentPolicyTest {

  @Test
  void onlyActiveNonDeletedStudentsAreBillable() {
    assertTrue(BillableEnrollmentPolicy.isBillable("ACTIVE", false));
    assertTrue(BillableEnrollmentPolicy.isBillable(" active ", false));
    assertFalse(BillableEnrollmentPolicy.isBillable("ACTIVE", true));
    for (String status :
        List.of(
            "INACTIVE",
            "LEFT_SCHOOL",
            "TRANSFERRED",
            "TC_ISSUED",
            "ALUMNI",
            "SUSPENDED",
            "EXPELLED",
            "PASSED_OUT",
            "PROMOTED",
            "DROPOUT",
            "DELETED")) {
      assertFalse(BillableEnrollmentPolicy.isBillable(status, false), status);
    }
  }

  @Test
  void billableStatusConstantMatchesTheCountQuery() {
    assertEquals("ACTIVE", BillableEnrollmentPolicy.BILLABLE_STATUS);
  }
}
