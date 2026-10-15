package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.fee.finance.FeeConcessionCalculator.ChildRule;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator.FeeLine;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator.Result;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator.SiblingPolicy;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator.StudentAdjustment;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeeConcessionCalculatorTest {

  private static final List<FeeLine> LINES =
      List.of(new FeeLine("TUITION", new BigDecimal("30000")), new FeeLine("LIBRARY", new BigDecimal("500")));

  private static SiblingPolicy policy(String stacking) {
    return new SiblingPolicy(
        true,
        List.of("TUITION"),
        stacking,
        new BigDecimal("100"),
        List.of(
            new ChildRule(1, "PERCENT", BigDecimal.ZERO),
            new ChildRule(2, "PERCENT", new BigDecimal("10")),
            new ChildRule(3, "PERCENT", new BigDecimal("15"))),
        "PERCENT",
        new BigDecimal("15"));
  }

  @Test
  void secondChildGetsTenPercentOfTuitionOnly() {
    Result result =
        FeeConcessionCalculator.calculate(
            LINES,
            BigDecimal.ZERO,
            List.of(),
            null,
            policy(FeeConcessionCalculator.STACK_STUDENT_THEN_SIBLING),
            List.of("A-1", "A-2"),
            "A-2",
            false);
    assertEquals(2, result.siblingOrder());
    assertEquals(new BigDecimal("3000.00"), result.siblingConcession());
    assertEquals(new BigDecimal("27500.00"), result.payable());
    assertEquals(List.of("SIBLING:2"), result.sources());
  }

  @Test
  void firstChildAndOnlyChildGetNoSiblingConcession() {
    Result first =
        FeeConcessionCalculator.calculate(
            LINES, BigDecimal.ZERO, List.of(), null, policy("STUDENT_THEN_SIBLING"),
            List.of("A-1", "A-2"), "A-1", false);
    assertEquals(new BigDecimal("0.00"), first.siblingConcession());
    assertEquals(new BigDecimal("30500.00"), first.payable());

    Result only =
        FeeConcessionCalculator.calculate(
            LINES, BigDecimal.ZERO, List.of(), null, policy("STUDENT_THEN_SIBLING"),
            List.of("A-1"), "A-1", false);
    assertFalse(only.siblingApplied());
    assertEquals(new BigDecimal("30500.00"), only.payable());
  }

  @Test
  void customPayableReducesOneStudentWithoutChangingTheStandardLines() {
    StudentAdjustment adjustment =
        new StudentAdjustment("adj-1", "CUSTOM_PAYABLE", new BigDecimal("24000"), List.of("TUITION"), "APPROVED");
    Result reduced =
        FeeConcessionCalculator.calculate(
            LINES, BigDecimal.ZERO, List.of(), adjustment, null, List.of("A-1"), "A-1", false);
    Result classmate =
        FeeConcessionCalculator.calculate(
            LINES, BigDecimal.ZERO, List.of(), null, null, List.of("B-1"), "B-1", false);
    assertEquals(new BigDecimal("6000.00"), reduced.studentConcession());
    assertEquals(new BigDecimal("24500.00"), reduced.payable());
    assertEquals(new BigDecimal("30500.00"), classmate.payable());
    assertEquals(new BigDecimal("30000"), LINES.get(0).amount());
  }

  @Test
  void studentThenSiblingCombinesOnTheReducedTuition() {
    StudentAdjustment adjustment =
        new StudentAdjustment("adj-1", "FIXED_OFF", new BigDecimal("6000"), List.of("TUITION"), "APPROVED");
    Result result =
        FeeConcessionCalculator.calculate(
            LINES,
            BigDecimal.ZERO,
            List.of(),
            adjustment,
            policy(FeeConcessionCalculator.STACK_STUDENT_THEN_SIBLING),
            List.of("A-1", "A-2"),
            "A-2",
            false);
    assertEquals(new BigDecimal("6000.00"), result.studentConcession());
    assertEquals(new BigDecimal("2400.00"), result.siblingConcession());
    assertEquals(new BigDecimal("22100.00"), result.payable());
    assertEquals(List.of("STUDENT:adj-1", "SIBLING:2"), result.sources());
  }

  @Test
  void exclusiveStudentSkipsSiblingAndHigherOnlyKeepsTheLarger() {
    StudentAdjustment adjustment =
        new StudentAdjustment("adj-1", "PERCENT", new BigDecimal("5"), List.of("TUITION"), "APPROVED");
    Result exclusive =
        FeeConcessionCalculator.calculate(
            LINES, BigDecimal.ZERO, List.of(), adjustment,
            policy(FeeConcessionCalculator.STACK_EXCLUSIVE_STUDENT),
            List.of("A-1", "A-2"), "A-2", false);
    assertEquals(new BigDecimal("1500.00"), exclusive.studentConcession());
    assertEquals(new BigDecimal("0.00"), exclusive.siblingConcession());

    Result higher =
        FeeConcessionCalculator.calculate(
            LINES, BigDecimal.ZERO, List.of(), adjustment,
            policy(FeeConcessionCalculator.STACK_HIGHER_ONLY),
            List.of("A-1", "A-2"), "A-2", false);
    assertEquals(new BigDecimal("0.00"), higher.studentConcession());
    assertEquals(new BigDecimal("3000.00"), higher.siblingConcession());
    assertEquals(1, higher.sources().size());
  }

  @Test
  void payableCannotGoNegativeAndTheSameSourceIsNotRepeated() {
    StudentAdjustment adjustment =
        new StudentAdjustment("adj-1", "FIXED_OFF", new BigDecimal("999999"), List.of(), "APPROVED");
    Result result =
        FeeConcessionCalculator.calculate(
            LINES, new BigDecimal("1000"), List.of("LIBRARY"), adjustment,
            policy(FeeConcessionCalculator.STACK_STUDENT_THEN_SIBLING),
            List.of("A-1", "A-2", "A-3", "A-4"), "A-4", false);
    assertTrue(result.payable().compareTo(BigDecimal.ZERO) >= 0);
    assertTrue(result.totalConcession().compareTo(result.standardAmount()) <= 0);
    long siblingSources = result.sources().stream().filter(s -> s.startsWith("SIBLING:")).count();
    long studentSources = result.sources().stream().filter(s -> s.startsWith("STUDENT:")).count();
    assertTrue(siblingSources <= 1);
    // A student discount with no fee head is not spread across the bill.
    assertEquals(0, studentSources);
    assertEquals(new BigDecimal("0.00"), result.studentConcession());
  }

  @Test
  void pendingAdjustmentAndManualSiblingKeyDoNotApplyTwice() {
    StudentAdjustment pending =
        new StudentAdjustment("adj-1", "PERCENT", new BigDecimal("50"), List.of("TUITION"), "PENDING");
    Result pendingResult =
        FeeConcessionCalculator.calculate(
            LINES, BigDecimal.ZERO, List.of(), pending, policy("STUDENT_THEN_SIBLING"),
            List.of("A-1"), "A-1", false);
    assertFalse(pendingResult.studentApplied());

    Result skipped =
        FeeConcessionCalculator.calculate(
            LINES, new BigDecimal("3000"), List.of("TUITION"), null, policy("STUDENT_THEN_SIBLING"),
            List.of("A-1", "A-2"), "A-2", true);
    assertEquals(new BigDecimal("0.00"), skipped.siblingConcession());
    assertEquals(new BigDecimal("3000.00"), skipped.otherConcession());
  }

  @Test
  void fourthChildUsesTheSubsequentRule() {
    Result result =
        FeeConcessionCalculator.calculate(
            List.of(new FeeLine("TUITION", new BigDecimal("10000"))),
            BigDecimal.ZERO,
            List.of(),
            null,
            policy("STUDENT_THEN_SIBLING"),
            List.of("A-1", "A-2", "A-3", "A-4"),
            "A-4",
            false);
    assertEquals(4, result.siblingOrder());
    assertEquals(new BigDecimal("1500.00"), result.siblingConcession());
  }
}
