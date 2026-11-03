package com.sugamflow.school.subscription.billing;

import java.time.Instant;

/** Amounts already calculated. The issuer persists them and does not reprice the line. */
public record PerStudentInvoiceCommand(
    String organizationId,
    String periodKey,
    int quantity,
    long unitAmountMinor,
    long baseFeeMinor,
    long discountMinor,
    long taxableMinor,
    long cgstMinor,
    long sgstMinor,
    long igstMinor,
    long taxMinor,
    long totalMinor,
    String taxRuleId,
    String sellerStateCode,
    String placeOfSupply,
    Instant periodStart,
    Instant periodEnd) {}
