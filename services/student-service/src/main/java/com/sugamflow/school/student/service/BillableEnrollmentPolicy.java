package com.sugamflow.school.student.service;

/**
 * Platform subscription count policy {@code ACTIVE_ON_RUN}. Only a non-deleted {@code ACTIVE}
 * student record is billable. Withdrawn, alumni, and other lifecycle statuses are not.
 */
public final class BillableEnrollmentPolicy {

  public static final String BILLABLE_STATUS = "ACTIVE";

  private BillableEnrollmentPolicy() {}

  public static boolean isBillable(String status, boolean deleted) {
    if (deleted || status == null || status.isBlank()) {
      return false;
    }
    return BILLABLE_STATUS.equalsIgnoreCase(status.trim());
  }
}
