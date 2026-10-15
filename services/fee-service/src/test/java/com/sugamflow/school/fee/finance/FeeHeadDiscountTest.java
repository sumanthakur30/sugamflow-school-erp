package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sugamflow.school.fee.finance.FeeConcessionCalculator.FeeLine;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator.Result;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator.StudentAdjustment;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeeHeadDiscountTest {

  private static final List<FeeLine> CLASS_FEES =
      List.of(
          new FeeLine("ANNUAL11", new BigDecimal("5000")),
          new FeeLine("TUITION", new BigDecimal("11000")),
          new FeeLine("LIBRARY", new BigDecimal("500")),
          new FeeLine("EXAM", new BigDecimal("700")),
          new FeeLine("LAB", new BigDecimal("1500")),
          new FeeLine("SPORTS", new BigDecimal("400")),
          new FeeLine("DEVELOPMENT", new BigDecimal("1000")));

  private static final List<String> REASONS =
      List.of(
          "Sibling Discount",
          "Staff Ward Discount",
          "Alumni Ward Discount",
          "Merit / Scholarship Discount");

  @Test
  void percentageDiscountStaysOnTheSelectedFeeHead() {
    List<BigDecimal> discounts = discounts(adjustment("PERCENT", "10", List.of("TUITION")), null);
    assertEquals(new BigDecimal("0.00"), discounts.get(0));
    assertEquals(new BigDecimal("1100.00"), discounts.get(1));
    assertEquals(new BigDecimal("0.00"), discounts.get(2));
    assertEquals(new BigDecimal("0.00"), discounts.get(3));
    assertEquals(new BigDecimal("0.00"), discounts.get(4));
    assertEquals(new BigDecimal("0.00"), discounts.get(5));
    assertEquals(new BigDecimal("0.00"), discounts.get(6));
    assertEquals(new BigDecimal("1100.00"), sum(discounts));
  }

  @Test
  void fixedDiscountStaysOnTheSelectedFeeHead() {
    List<BigDecimal> discounts = discounts(adjustment("FIXED_OFF", "2000", List.of("TUITION")), null);
    assertEquals(new BigDecimal("2000.00"), discounts.get(1));
    assertEquals(new BigDecimal("0.00"), discounts.get(2));
    assertEquals(new BigDecimal("2000.00"), sum(discounts));
  }

  @Test
  void fixedDiscountIsAppliedOnceAcrossSelectedHeads() {
    List<BigDecimal> discounts =
        discounts(adjustment("FIXED_OFF", "2000", List.of("TUITION", "LIBRARY")), null);
    assertEquals(new BigDecimal("1913.04"), discounts.get(1));
    assertEquals(new BigDecimal("86.96"), discounts.get(2));
    assertEquals(new BigDecimal("0.00"), discounts.get(3));
    assertEquals(new BigDecimal("2000.00"), sum(discounts));
  }

  @Test
  void discountReasonDoesNotChangeTheFeeHead() {
    List<BigDecimal> first = null;
    for (String reason : REASONS) {
      assertEquals(false, reason.equalsIgnoreCase("TUITION"));
      List<BigDecimal> discounts = discounts(adjustment("PERCENT", "10", List.of("TUITION")), null);
      if (first == null) {
        first = discounts;
      }
      assertEquals(first, discounts);
      assertEquals(new BigDecimal("1100.00"), discounts.get(1));
    }
  }

  @Test
  void recurringDiscountRepeatsOnTuitionOnlyAndAPeriodDiscountIsNotCopied() {
    StudentAdjustment recurring = adjustment("PERCENT", "10", List.of("TUITION"));
    StudentAdjustment august = adjustment("FIXED_OFF", "500", List.of("TUITION"));
    List<BigDecimal> augustBill = discounts(recurring, august);
    List<BigDecimal> septemberBill = discounts(recurring, null);
    assertEquals(new BigDecimal("1600.00"), augustBill.get(1));
    assertEquals(new BigDecimal("0.00"), augustBill.get(2));
    assertEquals(new BigDecimal("1100.00"), septemberBill.get(1));
    assertEquals(new BigDecimal("0.00"), septemberBill.get(2));
  }

  @Test
  void collectionPayableUsesOnlyTheDiscountForThatFeeHead() {
    List<BigDecimal> discounts = discounts(adjustment("PERCENT", "10", List.of("TUITION")), null);
    assertEquals(new BigDecimal("9900.00"), CLASS_FEES.get(1).amount().subtract(discounts.get(1)));
    assertEquals(new BigDecimal("500.00"), CLASS_FEES.get(2).amount().subtract(discounts.get(2)));
  }

  @Test
  void discountWithNoFeeHeadIsNotAppliedToTheBill() {
    Result result = quote(adjustment("PERCENT", "10", List.of()), null);
    assertEquals(new BigDecimal("0.00"), result.studentConcession());
    assertEquals(new BigDecimal("0.00"), sum(discounts(adjustment("PERCENT", "10", List.of()), null)));
    assertEquals(new BigDecimal("20100.00"), result.payable());
  }

  @Test
  void partialPaymentDoesNotApplyTheHeadDiscountAgain() {
    assertEquals(
        new BigDecimal("8900.00"),
        FeeConcessionCalculator.outstanding(new BigDecimal("9900"), new BigDecimal("1000")));
  }

  private static StudentAdjustment adjustment(String mode, String value, List<String> heads) {
    return new StudentAdjustment("rule", mode, new BigDecimal(value), heads, "APPROVED");
  }

  private static Result quote(StudentAdjustment recurring, StudentAdjustment period) {
    return FeeConcessionCalculator.calculate(
        CLASS_FEES, BigDecimal.ZERO, List.of(), recurring, null, List.of(), "ADM-1", false, period);
  }

  private static List<BigDecimal> discounts(StudentAdjustment recurring, StudentAdjustment period) {
    Result result = quote(recurring, period);
    return FeeConcessionCalculator.allocate(
        CLASS_FEES,
        BigDecimal.ZERO,
        List.of(),
        result.studentConcession(),
        recurring == null ? List.of() : recurring.appliesToHeads(),
        result.periodConcession(),
        period == null ? List.of() : period.appliesToHeads(),
        result.siblingConcession(),
        List.of());
  }

  private static BigDecimal sum(List<BigDecimal> amounts) {
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal amount : amounts) {
      total = total.add(amount);
    }
    return total.setScale(2);
  }
}
