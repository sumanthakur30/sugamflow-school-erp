package com.sugamflow.school.subscription.billing;

import java.util.Map;

public interface PerStudentInvoiceIssuer {
  TaxRuleView defaultTaxRule();

  /** Persists an ISSUED invoice. Does not mark it paid. */
  Map<String, Object> issuePerStudent(PerStudentInvoiceCommand command);
}
