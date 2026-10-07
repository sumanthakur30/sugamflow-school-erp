package com.sugamflow.school.subscription.pricing;

/** Paise arithmetic for the rate card. Does not read or write subscription prices. */
public final class RateCardMath {

  public static final int GST_18_BPS = 1800;

  private RateCardMath() {}

  public static long discountMinor(long listMinor, int discountBps) {
    long list = Math.max(0, listMinor);
    int bps = Math.max(0, Math.min(10_000, discountBps));
    return list * bps / 10_000L;
  }

  public static long sellingMinor(long listMinor, int discountBps) {
    return Math.max(0, listMinor) - discountMinor(listMinor, discountBps);
  }

  /** Basis points so a proposed rupee amount can be shown as a discount percent. */
  public static int bpsFromPrices(long standardMinor, long proposalMinor) {
    long standard = Math.max(0, standardMinor);
    long proposal = Math.max(0, proposalMinor);
    if (standard == 0 || proposal >= standard) {
      return 0;
    }
    long discount = standard - proposal;
    return (int) Math.round((discount * 10_000.0) / standard);
  }

  public static boolean approvalRequired(int discountBps, int maxDiscountBps) {
    return discountBps > Math.max(0, maxDiscountBps);
  }

  /**
   * Half-up rounding of a paise amount. RUPEE is ₹1, TEN is ₹10, HUNDRED is ₹100.
   * NONE and any other step leave the paise unchanged.
   */
  public static long roundMinor(long amountMinor, String step) {
    long amount = Math.max(0, amountMinor);
    long unit =
        switch (step == null ? "NONE" : step.trim().toUpperCase(java.util.Locale.ROOT)) {
          case "RUPEE" -> 100L;
          case "TEN" -> 1_000L;
          case "HUNDRED" -> 10_000L;
          default -> 1L;
        };
    if (unit <= 1) {
      return amount;
    }
    return Math.round(amount / (double) unit) * unit;
  }

  /** GST portion. Inclusive amounts already contain the tax. */
  public static long gstMinor(long amountMinor, int gstBps, boolean inclusive) {
    long amount = Math.max(0, amountMinor);
    int bps = Math.max(0, gstBps);
    if (amount == 0 || bps == 0) {
      return 0;
    }
    if (inclusive) {
      return amount * bps / (10_000L + bps);
    }
    return amount * bps / 10_000L;
  }

  /** A business-type rate replaces the standard rate. A missing rate is zero, not a guessed price. */
  public static long rateMinor(Long businessTypeRate, Long standardRate) {
    if (businessTypeRate != null) {
      return Math.max(0, businessTypeRate);
    }
    return standardRate == null ? 0 : Math.max(0, standardRate);
  }
}
