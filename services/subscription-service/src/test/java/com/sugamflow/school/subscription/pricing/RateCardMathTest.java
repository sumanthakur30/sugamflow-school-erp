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
  void businessTypeRateOverridesTheStandardRate() {
    assertEquals(699_00L, RateCardMath.rateMinor(699_00L, 499_00L));
    assertEquals(499_00L, RateCardMath.rateMinor(null, 499_00L));
    assertEquals(0L, RateCardMath.rateMinor(null, null));
  }
}
