package com.sugamflow.school.subscription.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money math for a school per-student platform invoice. Amounts are paise. */
public final class SchoolPerStudentBillingMath {

  private SchoolPerStudentBillingMath() {}

  public record Charge(
      long studentAmountMinor, long baseFeeMinor, long discountMinor, long preTaxMinor) {}

  public record TaxSplit(long cgstMinor, long sgstMinor, long igstMinor, long taxMinor, long taxableMinor, long totalMinor) {}

  public static Charge charge(int studentCount, long unitAmountMinor, long baseFeeMinor, long discountMinor) {
    if (studentCount < 0 || unitAmountMinor < 0 || baseFeeMinor < 0 || discountMinor < 0) {
      throw new IllegalArgumentException("Student count and money amounts must be >= 0");
    }
    long studentAmount = Math.multiplyExact((long) studentCount, unitAmountMinor);
    long gross = Math.addExact(studentAmount, baseFeeMinor);
    long discount = Math.min(discountMinor, gross);
    return new Charge(studentAmount, baseFeeMinor, discount, gross - discount);
  }

  /**
   * Applies the supplied tax-rule basis points. Exclusive prices add tax. Inclusive prices extract
   * tax from {@code preTaxMinor}, which is then the gross amount.
   */
  public static TaxSplit tax(
      long preTaxMinor, int cgstBps, int sgstBps, int igstBps, boolean interState, boolean gstInclusive) {
    if (preTaxMinor < 0 || cgstBps < 0 || sgstBps < 0 || igstBps < 0) {
      throw new IllegalArgumentException("Tax inputs must be >= 0");
    }
    int rateBps = interState ? igstBps : cgstBps + sgstBps;
    if (gstInclusive) {
      long tax = extractInclusive(preTaxMinor, rateBps);
      long taxable = preTaxMinor - tax;
      return split(taxable, tax, preTaxMinor, cgstBps, sgstBps, interState, rateBps);
    }
    long tax = bps(preTaxMinor, rateBps);
    long total = Math.addExact(preTaxMinor, tax);
    return split(preTaxMinor, tax, total, cgstBps, sgstBps, interState, rateBps);
  }

  private static TaxSplit split(
      long taxable,
      long tax,
      long total,
      int cgstBps,
      int sgstBps,
      boolean interState,
      int rateBps) {
    if (tax == 0 || rateBps == 0) {
      return new TaxSplit(0, 0, 0, 0, taxable, total);
    }
    if (interState) {
      return new TaxSplit(0, 0, tax, tax, taxable, total);
    }
    long cgst = bps(taxable, cgstBps);
    long sgst = tax - cgst;
    if (sgstBps == 0) {
      sgst = 0;
      cgst = tax;
    }
    return new TaxSplit(cgst, sgst, 0, tax, taxable, total);
  }

  static long bps(long amount, int basisPoints) {
    return BigDecimal.valueOf(amount)
        .multiply(BigDecimal.valueOf(basisPoints))
        .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
        .longValue();
  }

  private static long extractInclusive(long gross, int basisPoints) {
    if (basisPoints == 0 || gross == 0) {
      return 0;
    }
    return BigDecimal.valueOf(gross)
        .multiply(BigDecimal.valueOf(basisPoints))
        .divide(BigDecimal.valueOf(10_000L + basisPoints), 0, RoundingMode.HALF_UP)
        .longValue();
  }
}
