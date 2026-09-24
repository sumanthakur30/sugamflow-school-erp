package com.sugamflow.school.compliance.dto;

import java.time.Instant;

public record ValidationFindingResponse(
    Long id,
    Long campaignId,
    String entityType,
    String entityId,
    String entityLabel,
    String fieldKey,
    String ruleCode,
    String severity,
    String message,
    String suggestion,
    String status,
    String source,
    java.math.BigDecimal confidence,
    Instant createdAt) {}
