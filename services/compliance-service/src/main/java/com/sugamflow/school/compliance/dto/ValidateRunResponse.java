package com.sugamflow.school.compliance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ValidateRunResponse(
    Long campaignId,
    String campaignTitle,
    String status,
    BigDecimal complianceScore,
    int recordsChecked,
    int blockerCount,
    int warnCount,
    Instant validatedAt) {}
