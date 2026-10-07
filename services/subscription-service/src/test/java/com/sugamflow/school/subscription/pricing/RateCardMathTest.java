package com.sugamflow.school.subscription.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RateCardMathTest {

  @Test
  void bundleDiscountReducesTheFeatureTotalWithoutChangingTheList() {
    long list = 850_000L;
    assertEquals(212_500L, RateCardMath.discountMinor(list, 2500));
    assertEquals(637_500L, RateCardMath.sellingMinor(list, 2500));
  }

  @Test
  void gstIsAddedWhenExclusiveAndExtractedWhenInclusive() {
    assertEquals(18_000L, RateCardMath.gstMinor(100_000L, RateCardMath.GST_18_BPS, false));
    assertEquals(15_254L, RateCardMath.gstMinor(100_000L, RateCardMath.GST_18_BPS, true));
  }

  @Test
  void proposalAmountCalculatesDiscountWithoutChangingTheStandard() {
    long standard = 499_900L;
    long proposal = 350_000L;
    int bps = RateCardMath.bpsFromPrices(standard, proposal);
    assertEquals(2999, bps);
    assertEquals(true, RateCardMath.approvalRequired(bps, 2000));
    assertEquals(false, RateCardMath.approvalRequired(1500, 2000));
    assertEquals(0, RateCardMath.bpsFromPrices(standard, standard));
  }

  @Test
  void switchesAFreeFormQuoteBetweenMonthlyAndYearly() {
    long monthly = 249_900L;
    long yearly = RateCardMath.cycleStandardMinor(monthly, false, true);
    assertEquals(2_998_800L, yearly);
    assertEquals(monthly, RateCardMath.cycleStandardMinor(yearly, true, false));
    assertEquals(100_000L, RateCardMath.roundMinor(RateCardMath.sellingMinor(monthly, 6000), "RUPEE"));
    assertEquals(799_600L, RateCardMath.pendingMinor(1_799_300L, 999_700L));
    assertEquals(0L, RateCardMath.pendingMinor(100_000L, 100_000L));
  }

  @Test
  void roundsTheCustomerPriceWithoutChangingTheStandard() {
    assertEquals(100_000L, RateCardMath.roundMinor(99_960L, "RUPEE"));
    assertEquals(125_000L, RateCardMath.roundMinor(124_950L, "RUPEE"));
    assertEquals(125_000L, RateCardMath.roundMinor(124_950L, "TEN"));
    assertEquals(120_000L, RateCardMath.roundMinor(124_950L, "HUNDRED"));
    assertEquals(99_960L, RateCardMath.roundMinor(99_960L, "NONE"));
    assertEquals(99_960L, RateCardMath.sellingMinor(249_900L, 6000));
  }

  @Test
  void businessTypeRateOverridesTheStandardRate() {
    assertEquals(699_00L, RateCardMath.rateMinor(699_00L, 499_00L));
    assertEquals(499_00L, RateCardMath.rateMinor(null, 499_00L));
    assertEquals(0L, RateCardMath.rateMinor(null, null));
  }
}
