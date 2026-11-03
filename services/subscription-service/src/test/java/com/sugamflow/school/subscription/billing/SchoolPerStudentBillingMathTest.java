package com.sugamflow.school.subscription.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SchoolPerStudentBillingMathTest {

  @Test
  void eightHundredStudentsAtThirtyRupeesBeforeTax() {
    SchoolPerStudentBillingMath.Charge charge = SchoolPerStudentBillingMath.charge(800, 3_000L, 0, 0);
    assertEquals(2_400_000L, charge.preTaxMinor());
    SchoolPerStudentBillingMath.TaxSplit tax =
        SchoolPerStudentBillingMath.tax(charge.preTaxMinor(), 900, 900, 1800, false, false);
    assertEquals(2_400_000L, tax.taxableMinor());
    assertEquals(216_000L, tax.cgstMinor());
    assertEquals(216_000L, tax.sgstMinor());
    assertEquals(0L, tax.igstMinor());
    assertEquals(2_832_000L, tax.totalMinor());
  }

  @Test
  void interStateUsesIgstFromTheSuppliedRule() {
    SchoolPerStudentBillingMath.TaxSplit tax =
        SchoolPerStudentBillingMath.tax(2_400_000L, 900, 900, 1800, true, false);
    assertEquals(0L, tax.cgstMinor());
    assertEquals(0L, tax.sgstMinor());
    assertEquals(432_000L, tax.igstMinor());
    assertEquals(2_832_000L, tax.totalMinor());
  }
}
