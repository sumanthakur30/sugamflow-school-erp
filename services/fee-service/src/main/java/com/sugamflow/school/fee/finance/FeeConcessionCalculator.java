package com.sugamflow.school.fee.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies the standard fee first, then one approved student adjustment, then the sibling
 * policy. The same source is recorded once. The payable amount cannot go below zero.
 */
public final class FeeConcessionCalculator {

  public static final String STACK_STUDENT_THEN_SIBLING = "STUDENT_THEN_SIBLING";
  public static final String STACK_EXCLUSIVE_STUDENT = "EXCLUSIVE_STUDENT";
  public static final String STACK_HIGHER_ONLY = "HIGHER_ONLY";

  private FeeConcessionCalculator() {}

  public record FeeLine(String headKey, BigDecimal amount) {}

  public record StudentAdjustment(
      String id, String mode, BigDecimal value, List<String> appliesToHeads, String status) {}

  public record ChildRule(int order, String type, BigDecimal value) {}

  public record SiblingPolicy(
      boolean enabled,
      List<String> appliesToHeads,
      String stacking,
      BigDecimal maxDiscountPercent,
      List<ChildRule> childRules,
      String subsequentType,
      BigDecimal subsequentValue) {}

  public record Result(
      BigDecimal standardAmount,
      BigDecimal otherConcession,
      BigDecimal studentConcession,
      BigDecimal periodConcession,
      BigDecimal siblingConcession,
      BigDecimal totalConcession,
      BigDecimal payable,
      int siblingOrder,
      int householdSize,
      boolean studentApplied,
      boolean periodApplied,
      boolean siblingApplied,
      String stacking,
      List<String> sources) {

    public Map<String, Object> toMap() {
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("standardAmount", money(standardAmount));
      out.put("otherConcession", money(otherConcession));
      out.put("studentConcession", money(studentConcession));
      out.put("periodConcession", money(periodConcession));
      out.put("siblingConcession", money(siblingConcession));
      out.put("totalConcession", money(totalConcession));
      out.put("payable", money(payable));
      out.put("siblingOrder", siblingOrder);
      out.put("householdSize", householdSize);
      out.put("studentApplied", studentApplied);
      out.put("periodApplied", periodApplied);
      out.put("siblingApplied", siblingApplied);
      out.put("stacking", stacking);
      out.put("sources", sources);
      return out;
    }
  }

  public static Result calculate(
      List<FeeLine> lines,
      BigDecimal otherConcession,
      List<String> otherHeads,
      StudentAdjustment student,
      SiblingPolicy sibling,
      List<String> householdAdmissionNos,
      String studentRef,
      boolean skipSibling) {
    return calculate(
        lines, otherConcession, otherHeads, student, sibling, householdAdmissionNos, studentRef, skipSibling, null);
  }

  public static Result calculate(
      List<FeeLine> lines,
      BigDecimal otherConcession,
      List<String> otherHeads,
      StudentAdjustment student,
      SiblingPolicy sibling,
      List<String> householdAdmissionNos,
      String studentRef,
      boolean skipSibling,
      StudentAdjustment period) {
    List<FeeLine> safeLines = lines == null ? List.of() : lines;
    BigDecimal standard = money(sum(safeLines));
    BigDecimal other = money(nz(otherConcession)).min(standard).max(BigDecimal.ZERO);

    List<String> household = rankHousehold(householdAdmissionNos, studentRef);
    int householdSize = household.size();
    int order = siblingOrder(household, studentRef);

    String stacking =
        sibling == null || sibling.stacking() == null || sibling.stacking().isBlank()
            ? STACK_STUDENT_THEN_SIBLING
            : sibling.stacking().trim().toUpperCase(Locale.ROOT);

    List<String> studentHeads = student == null ? List.of() : student.appliesToHeads();
    List<String> siblingHeads = sibling == null ? List.of() : sibling.appliesToHeads();
    BigDecimal studentBase =
        eligible(safeLines, studentHeads).subtract(overlap(other, otherHeads, studentHeads, safeLines)).max(BigDecimal.ZERO);
    BigDecimal siblingBase =
        eligible(safeLines, siblingHeads).subtract(overlap(other, otherHeads, siblingHeads, safeLines)).max(BigDecimal.ZERO);

    BigDecimal studentDiscount = BigDecimal.ZERO;
    boolean studentApplied = false;
    List<String> sources = new ArrayList<>();
    if (student != null && "APPROVED".equalsIgnoreCase(student.status()) && !already(sources, "STUDENT:" + student.id())) {
      studentDiscount = money(modeDiscount(student.mode(), student.value(), studentBase)).min(studentBase);
      if (studentDiscount.compareTo(BigDecimal.ZERO) > 0) {
        studentApplied = true;
        sources.add("STUDENT:" + student.id());
      }
    }

    BigDecimal periodDiscount = BigDecimal.ZERO;
    boolean periodApplied = false;
    List<String> periodHeads = period == null ? List.of() : period.appliesToHeads();
    BigDecimal periodBase = BigDecimal.ZERO;
    if (period != null && "APPROVED".equalsIgnoreCase(period.status()) && !already(sources, "PERIOD:" + period.id())) {
      BigDecimal periodEligible =
          eligible(safeLines, periodHeads)
              .subtract(overlap(other, otherHeads, periodHeads, safeLines))
              .max(BigDecimal.ZERO);
      BigDecimal alreadyOff =
          share(studentDiscount, studentBase, intersection(safeLines, studentHeads, periodHeads));
      periodBase = periodEligible.subtract(alreadyOff).max(BigDecimal.ZERO);
      periodDiscount = money(modeDiscount(period.mode(), period.value(), periodBase)).min(periodBase);
      if (periodDiscount.compareTo(BigDecimal.ZERO) > 0) {
        periodApplied = true;
        sources.add("PERIOD:" + period.id());
      }
    }

    BigDecimal siblingDiscount = BigDecimal.ZERO;
    boolean siblingApplied = false;
    if (!skipSibling
        && sibling != null
        && sibling.enabled()
        && householdSize >= 2
        && !already(sources, "SIBLING")) {
      BigDecimal base = siblingBase;
      if (STACK_STUDENT_THEN_SIBLING.equals(stacking) && (studentApplied || periodApplied)) {
        BigDecimal sharedStudent =
            share(studentDiscount, studentBase, intersection(safeLines, studentHeads, siblingHeads));
        BigDecimal sharedPeriod =
            share(periodDiscount, periodBase, intersection(safeLines, periodHeads, siblingHeads));
        base = base.subtract(sharedStudent).subtract(sharedPeriod).max(BigDecimal.ZERO);
      }
      ChildRule rule = ruleFor(sibling, order);
      if (rule != null) {
        siblingDiscount = money(benefit(rule.type(), rule.value(), base)).min(base);
      }
      if (STACK_EXCLUSIVE_STUDENT.equals(stacking) && studentApplied) {
        siblingDiscount = BigDecimal.ZERO;
      } else if (STACK_HIGHER_ONLY.equals(stacking)) {
        if (studentDiscount.compareTo(siblingDiscount) >= 0) {
          siblingDiscount = BigDecimal.ZERO;
        } else {
          studentDiscount = BigDecimal.ZERO;
          studentApplied = false;
          sources.removeIf(s -> s.startsWith("STUDENT:"));
        }
      }
      if (siblingDiscount.compareTo(BigDecimal.ZERO) > 0) {
        siblingApplied = true;
        sources.add("SIBLING:" + order);
      }
    }

    BigDecimal maxPercent =
        sibling == null || sibling.maxDiscountPercent() == null
            ? BigDecimal.valueOf(100)
            : sibling.maxDiscountPercent();
    if (maxPercent.compareTo(BigDecimal.ZERO) <= 0) {
      maxPercent = BigDecimal.valueOf(100);
    }
    BigDecimal cap = standard.multiply(maxPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    BigDecimal room = cap.subtract(other).max(BigDecimal.ZERO);
    BigDecimal extra = studentDiscount.add(periodDiscount).add(siblingDiscount);
    if (extra.compareTo(room) > 0) {
      BigDecimal overflow = extra.subtract(room);
      BigDecimal fromSibling = siblingDiscount.min(overflow);
      siblingDiscount = siblingDiscount.subtract(fromSibling);
      overflow = overflow.subtract(fromSibling);
      BigDecimal fromPeriod = periodDiscount.min(overflow);
      periodDiscount = periodDiscount.subtract(fromPeriod);
      overflow = overflow.subtract(fromPeriod);
      studentDiscount = studentDiscount.subtract(overflow.min(studentDiscount)).max(BigDecimal.ZERO);
      siblingApplied = siblingDiscount.compareTo(BigDecimal.ZERO) > 0;
      periodApplied = periodDiscount.compareTo(BigDecimal.ZERO) > 0;
      studentApplied = studentDiscount.compareTo(BigDecimal.ZERO) > 0;
      if (!siblingApplied) {
        sources.removeIf(s -> s.startsWith("SIBLING:"));
      }
      if (!periodApplied) {
        sources.removeIf(s -> s.startsWith("PERIOD:"));
      }
      if (!studentApplied) {
        sources.removeIf(s -> s.startsWith("STUDENT:"));
      }
    }

    BigDecimal total = money(other.add(studentDiscount).add(periodDiscount).add(siblingDiscount)).min(standard);
    BigDecimal payable = standard.subtract(total).max(BigDecimal.ZERO);
    return new Result(
        standard,
        money(other),
        money(studentDiscount),
        money(periodDiscount),
        money(siblingDiscount),
        total,
        money(payable),
        order,
        householdSize,
        studentApplied,
        periodApplied,
        siblingApplied,
        stacking,
        List.copyOf(sources));
  }

  @SuppressWarnings("unchecked")
  public static SiblingPolicy siblingPolicy(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return null;
    }
    List<ChildRule> rules = new ArrayList<>();
    Object rawRules = payload.get("childRules");
    if (rawRules instanceof List<?> list) {
      for (Object item : list) {
        if (!(item instanceof Map<?, ?> map)) continue;
        rules.add(
            new ChildRule(
                intValue(map.get("order")),
                String.valueOf(map.get("type")),
                decimal(map.get("value"))));
      }
    }
    String subsequentType = null;
    BigDecimal subsequentValue = null;
    if (payload.get("subsequent") instanceof Map<?, ?> subsequent) {
      subsequentType = subsequent.get("type") == null ? "PERCENT" : String.valueOf(subsequent.get("type"));
      subsequentValue = decimal(subsequent.get("value"));
    }
    return new SiblingPolicy(
        !Boolean.FALSE.equals(payload.get("enabled")),
        heads(payload.get("appliesToHeads")),
        payload.get("stacking") == null ? STACK_STUDENT_THEN_SIBLING : String.valueOf(payload.get("stacking")),
        decimal(payload.get("maxDiscountPercent")),
        rules,
        subsequentType,
        subsequentValue);
  }

  public static StudentAdjustment studentAdjustment(
      String id, String mode, BigDecimal value, List<String> heads, String status) {
    return new StudentAdjustment(id, mode, value, heads == null ? List.of() : heads, status);
  }

  public static List<String> admissionNos(Object raw) {
    List<String> out = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item != null && !String.valueOf(item).isBlank()) {
          out.add(String.valueOf(item).trim());
        }
      }
      return out;
    }
    if (raw != null) {
      for (String part : String.valueOf(raw).split(",")) {
        if (!part.isBlank()) {
          out.add(part.trim());
        }
      }
    }
    return out;
  }

  public static List<String> heads(Object raw) {
    return admissionNos(raw).stream().map(h -> h.toUpperCase(Locale.ROOT)).toList();
  }

  private static int intValue(Object raw) {
    if (raw instanceof Number number) {
      return number.intValue();
    }
    if (raw == null || String.valueOf(raw).isBlank()) {
      return 0;
    }
    return Integer.parseInt(String.valueOf(raw).trim());
  }

  private static BigDecimal decimal(Object raw) {
    if (raw instanceof BigDecimal decimal) {
      return decimal;
    }
    if (raw instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    if (raw == null || String.valueOf(raw).isBlank()) {
      return BigDecimal.ZERO;
    }
    return new BigDecimal(String.valueOf(raw).trim());
  }

  public static List<String> rankHousehold(List<String> admissionNos, String studentRef) {
    Set<String> family = new LinkedHashSet<>();
    if (admissionNos != null) {
      for (String raw : admissionNos) {
        if (raw != null && !raw.isBlank()) {
          family.add(raw.trim());
        }
      }
    }
    if (studentRef != null && !studentRef.isBlank()) {
      family.add(studentRef.trim());
    }
    return family.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
  }

  public static int siblingOrder(List<String> ranked, String studentRef) {
    if (studentRef == null || ranked == null || ranked.isEmpty()) {
      return 1;
    }
    for (int i = 0; i < ranked.size(); i++) {
      if (ranked.get(i).equalsIgnoreCase(studentRef.trim())) {
        return i + 1;
      }
    }
    return 1;
  }

  private static ChildRule ruleFor(SiblingPolicy policy, int order) {
    ChildRule exact = null;
    int highest = 0;
    if (policy.childRules() != null) {
      for (ChildRule rule : policy.childRules()) {
        if (rule == null) continue;
        if (rule.order() == order) {
          exact = rule;
        }
        if (rule.order() > highest) {
          highest = rule.order();
        }
      }
    }
    if (exact != null) {
      return exact;
    }
    if (order > highest && policy.subsequentType() != null && policy.subsequentValue() != null) {
      return new ChildRule(order, policy.subsequentType(), policy.subsequentValue());
    }
    return null;
  }

  private static BigDecimal modeDiscount(String mode, BigDecimal value, BigDecimal base) {
    String kind = mode == null ? "FIXED_OFF" : mode.trim().toUpperCase(Locale.ROOT);
    if ("CUSTOM_PAYABLE".equals(kind)) {
      BigDecimal target = nz(value).max(BigDecimal.ZERO);
      return base.subtract(target).max(BigDecimal.ZERO);
    }
    if ("PERCENT".equals(kind)) {
      return benefit("PERCENT", value, base);
    }
    return benefit("FIXED", value, base);
  }

  private static BigDecimal benefit(String type, BigDecimal value, BigDecimal base) {
    BigDecimal amount = nz(value);
    if ("FIXED".equalsIgnoreCase(type) || "FIXED_OFF".equalsIgnoreCase(type)) {
      return amount.max(BigDecimal.ZERO).min(base);
    }
    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      amount = BigDecimal.ZERO;
    }
    if (amount.compareTo(BigDecimal.valueOf(100)) > 0) {
      amount = BigDecimal.valueOf(100);
    }
    return base.multiply(amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal eligible(List<FeeLine> lines, List<String> heads) {
    if (heads == null || heads.isEmpty()) {
      return sum(lines);
    }
    BigDecimal total = BigDecimal.ZERO;
    for (FeeLine line : lines) {
      if (containsHead(heads, line.headKey())) {
        total = total.add(nz(line.amount()));
      }
    }
    return total;
  }

  private static BigDecimal intersection(List<FeeLine> lines, List<String> left, List<String> right) {
    if ((left == null || left.isEmpty()) && (right == null || right.isEmpty())) {
      return sum(lines);
    }
    BigDecimal total = BigDecimal.ZERO;
    for (FeeLine line : lines) {
      boolean inLeft = left == null || left.isEmpty() || containsHead(left, line.headKey());
      boolean inRight = right == null || right.isEmpty() || containsHead(right, line.headKey());
      if (inLeft && inRight) {
        total = total.add(nz(line.amount()));
      }
    }
    return total;
  }

  private static BigDecimal overlap(
      BigDecimal other, List<String> otherHeads, List<String> targetHeads, List<FeeLine> lines) {
    if (other.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal otherBase = eligible(lines, otherHeads);
    if (otherBase.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal shared = intersection(lines, otherHeads, targetHeads);
    return other.multiply(shared).divide(otherBase, 2, RoundingMode.HALF_UP).min(other);
  }

  private static BigDecimal share(BigDecimal discount, BigDecimal discountBase, BigDecimal sharedBase) {
    if (discount.compareTo(BigDecimal.ZERO) <= 0 || discountBase.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    return discount.multiply(sharedBase.min(discountBase)).divide(discountBase, 2, RoundingMode.HALF_UP);
  }

  private static boolean containsHead(List<String> heads, String headKey) {
    if (headKey == null) return false;
    for (String head : heads) {
      if (head != null && head.equalsIgnoreCase(headKey)) {
        return true;
      }
    }
    return false;
  }

  private static boolean already(List<String> sources, String prefix) {
    for (String source : sources) {
      if (source.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static BigDecimal sum(List<FeeLine> lines) {
    BigDecimal total = BigDecimal.ZERO;
    for (FeeLine line : lines) {
      total = total.add(nz(line.amount()));
    }
    return total;
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private static BigDecimal money(BigDecimal value) {
    return nz(value).setScale(2, RoundingMode.HALF_UP);
  }

  /** Paid amount reduces the already-discounted payable. It does not apply the discount again. */
  public static BigDecimal outstanding(BigDecimal payable, BigDecimal paid) {
    return money(nz(payable).subtract(nz(paid))).max(BigDecimal.ZERO);
  }
}
