package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentDiscountRulesTest {

  @Test
  void percentMustBeGreaterThanZeroAndAtMostOneHundred() {
    assertEquals(
        "Percentage must be greater than 0 and no more than 100.",
        StudentDiscountRules.validate("PERCENT", BigDecimal.ZERO, "Merit / Scholarship Discount", null, null));
    assertEquals(
        "Percentage must be greater than 0 and no more than 100.",
        StudentDiscountRules.validate("PERCENT", new BigDecimal("101"), "Merit / Scholarship Discount", null, null));
    assertNull(
        StudentDiscountRules.validate("PERCENT", new BigDecimal("10"), "Merit / Scholarship Discount", null, null));
    assertNull(
        StudentDiscountRules.validate("PERCENT", new BigDecimal("100"), "Merit / Scholarship Discount", null, null));
  }

  @Test
  void fixedAmountMustBeGreaterThanZero() {
    assertEquals(
        "Fixed rupee discount must be greater than 0.",
        StudentDiscountRules.validate("FIXED_OFF", BigDecimal.ZERO, "Staff Ward Discount", null, null));
    assertNull(StudentDiscountRules.validate("FIXED_OFF", new BigDecimal("2000"), "Staff Ward Discount", null, null));
  }

  @Test
  void reasonIsRequiredAndOtherNeedsCustomText() {
    assertEquals(
        "A reason is required for a student fee adjustment",
        StudentDiscountRules.validate("PERCENT", BigDecimal.TEN, " ", null, null));
    assertEquals(
        "Enter a custom reason.",
        StudentDiscountRules.validate("FIXED_OFF", new BigDecimal("500"), "Other", null, null));
    assertEquals(
        "Enter a custom reason.",
        StudentDiscountRules.validate("FIXED_OFF", new BigDecimal("500"), "Other: ab", null, null));
    assertNull(
        StudentDiscountRules.validate("FIXED_OFF", new BigDecimal("500"), "Other: Board approval", null, null));
  }

  @Test
  void validUntilCannotBeEarlierThanValidFrom() {
    LocalDate from = LocalDate.of(2026, 4, 1);
    LocalDate to = LocalDate.of(2026, 3, 31);
    assertEquals(
        "Valid Until cannot be earlier than Valid From.",
        StudentDiscountRules.validate("PERCENT", BigDecimal.TEN, "Sibling Discount", from, to));
    assertNull(StudentDiscountRules.validate("PERCENT", BigDecimal.TEN, "Sibling Discount", from, from));
  }

  @Test
  void fixedDiscountRespectsEligibleFeeAndAPositiveOutstandingBalance() {
    BigDecimal value = new BigDecimal("2000");
    assertEquals(
        "Fixed discount cannot exceed the eligible fee.",
        StudentDiscountRules.fixedCap("FIXED_OFF", value, new BigDecimal("1500"), null));
    assertEquals(
        "Fixed discount cannot exceed the outstanding balance.",
        StudentDiscountRules.fixedCap("FIXED_OFF", value, new BigDecimal("5000"), new BigDecimal("1000")));
    assertNull(StudentDiscountRules.fixedCap("FIXED_OFF", value, new BigDecimal("5000"), BigDecimal.ZERO));
    assertNull(StudentDiscountRules.fixedCap("FIXED_OFF", value, null, null));
    assertNull(StudentDiscountRules.fixedCap("PERCENT", new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("100")));
  }
}
