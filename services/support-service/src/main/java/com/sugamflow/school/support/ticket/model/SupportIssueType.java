package com.sugamflow.school.support.ticket.model;

/** Shared catalog — school and shop UIs show a subset. */
public enum SupportIssueType {
  BUG_REPORT,
  BILLING_ISSUE,
  /** School */
  ADMISSION_ISSUE,
  FEE_ISSUE,
  ATTENDANCE_ISSUE,
  ACADEMICS_ISSUE,
  /** Shop (SugamFlow) */
  GST_ISSUE,
  INVENTORY_ISSUE,
  FEATURE_REQUEST,
  PERFORMANCE_ISSUE,
  OTHER
}
