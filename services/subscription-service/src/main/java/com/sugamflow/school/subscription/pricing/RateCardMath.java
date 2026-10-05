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
