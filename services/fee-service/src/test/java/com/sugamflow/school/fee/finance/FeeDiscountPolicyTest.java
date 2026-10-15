package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.fee.finance.FeeConcessionCalculator.FeeLine;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator.Result;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator.StudentAdjustment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeeDiscountPolicyTest {

  private static final List<FeeLine> TUITION = List.of(new FeeLine("TUITION", new BigDecimal("2000")));

  private static StudentAdjustment recurring(String mode, String value) {
    return new StudentAdjustment("recurring", mode, new BigDecimal(value), List.of("TUITION"), "APPROVED");
  }

  private static Result quote(StudentAdjustment recurring, StudentAdjustment period) {
    return FeeConcessionCalculator.calculate(
        TUITION, BigDecimal.ZERO, List.of(), recurring, null, List.of(), "ADM-1", false, period);
  }

  @Test
  void standardMonthlyFeeHasNoDiscount() {
    Result result = quote(null, null);
    assertEquals(new BigDecimal("2000.00"), result.payable());
    assertEquals(new BigDecimal("0.00"), result.totalConcession());
  }

  @Test
  void recurringPercentageAppliesToTheEligibleHead() {
    Result result = quote(recurring("PERCENT", "10"), null);
    assertEquals(new BigDecimal("200.00"), result.studentConcession());
    assertEquals(new BigDecimal("1800.00"), result.payable());
  }

  @Test
  void periodDiscountAppliesAfterTheRecurringDiscountAndOnlyWhenSupplied() {
    StudentAdjustment august =
        new StudentAdjustment("august", "FIXED_OFF", new BigDecimal("300"), List.of("TUITION"), "APPROVED");
    Result augustBill = quote(recurring("PERCENT", "10"), august);
    Result septemberBill = quote(recurring("PERCENT", "10"), null);
    assertEquals(new BigDecimal("200.00"), augustBill.studentConcession());
    assertEquals(new BigDecimal("300.00"), augustBill.periodConcession());
    assertEquals(new BigDecimal("1500.00"), augustBill.payable());
    assertEquals(new BigDecimal("1800.00"), septemberBill.payable());
    assertEquals(new BigDecimal("0.00"), septemberBill.periodConcession());
  }

  @Test
  void fixedRecurringDiscountStaysOnTheEligibleHead() {
    Result result = quote(recurring("FIXED_OFF", "250"), null);
    assertEquals(new BigDecimal("250.00"), result.studentConcession());
    assertEquals(new BigDecimal("1750.00"), result.payable());
  }

  @Test
  void fixedDiscountDoesNotExceedTheEligibleFee() {
    Result result = quote(recurring("FIXED_OFF", "5000"), null);
    assertEquals(new BigDecimal("2000.00"), result.studentConcession());
    assertEquals(new BigDecimal("0.00"), result.payable());
  }

  @Test
  void discountOutsideItsDatesDoesNotApply() {
    assertTrue(FeePeriodFilter.covers(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)));
    assertFalse(FeePeriodFilter.covers(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)));
  }

  @Test
  void payableNeverGoesBelowZero() {
    StudentAdjustment full = recurring("PERCENT", "100");
    StudentAdjustment extra =
        new StudentAdjustment("extra", "FIXED_OFF", new BigDecimal("300"), List.of("TUITION"), "APPROVED");
    Result small =
        FeeConcessionCalculator.calculate(
            List.of(new FeeLine("TUITION", new BigDecimal("500"))),
            BigDecimal.ZERO,
            List.of(),
            full,
            null,
            List.of(),
            "ADM-1",
            false,
            extra);
    assertEquals(new BigDecimal("0.00"), small.payable());
    assertTrue(small.payable().compareTo(BigDecimal.ZERO) >= 0);
  }

  @Test
  void partialPaymentReducesTheNetFeeWithoutApplyingTheDiscountAgain() {
    assertEquals(new BigDecimal("1000.00"), FeeConcessionCalculator.outstanding(new BigDecimal("1500"), new BigDecimal("500")));
  }

  @Test
  void repeatingTheSameDemandUsesTheSameIdempotencyKey() {
    String first = FeePeriodFilter.demandKey("HCP-01", "ADM-1", "grade_6", "2026-08");
    String second = FeePeriodFilter.demandKey("HCP-01", "ADM-1", "grade_6", "2026-08");
    assertEquals(first, second);
    assertTrue(first.startsWith("DEMAND|HCP-01|"));
  }

  @Test
  void calculationDoesNotRewriteTheOriginalFeeLines() {
    List<FeeLine> lines = List.of(new FeeLine("TUITION", new BigDecimal("2000")));
    FeeConcessionCalculator.calculate(
        lines, BigDecimal.ZERO, List.of(), recurring("PERCENT", "10"), null, List.of(), "ADM-1", false, null);
    assertEquals(new BigDecimal("2000"), lines.get(0).amount());
  }

  @Test
  void anotherSchoolPeriodDoesNotNormalizeToThisSchoolPeriod() {
    assertTrue(FeePeriodFilter.samePeriod("2026-08", "August 2026"));
    assertFalse(FeePeriodFilter.samePeriod("2026-08", "2026-09"));
  }

  @Test
  void monthlyBillIncludesTuitionAndSkipsAnnualAndOneTimeHeads() {
    assertTrue(FeePeriodFilter.include("M", 8, 4, false));
    assertFalse(FeePeriodFilter.include("Y", 8, 4, false));
    assertTrue(FeePeriodFilter.include("Y", 4, 4, false));
    assertTrue(FeePeriodFilter.include("Q", 7, 4, false));
    assertFalse(FeePeriodFilter.include("Q", 8, 4, false));
    assertTrue(FeePeriodFilter.include("HY", 10, 4, false));
    assertFalse(FeePeriodFilter.include("OT", 8, 4, false));
    assertTrue(FeePeriodFilter.include("OT", 8, 4, true));
  }
}
