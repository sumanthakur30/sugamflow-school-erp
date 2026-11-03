package com.sugamflow.school.subscription.billing;

public record TaxRuleView(String id, int cgstBps, int sgstBps, int igstBps) {}
