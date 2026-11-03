package com.sugamflow.school.subscription.billing;

public interface BillableStudentCounter {
  int countActive(String organizationId);
}
