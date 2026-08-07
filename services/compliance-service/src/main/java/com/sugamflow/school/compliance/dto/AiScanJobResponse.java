package com.sugamflow.school.compliance.dto;

import java.time.Instant;

public record AiScanJobResponse(
    Long id,
    Long campaignId,
    String status,
    String provider,
    int warnCount,
    int recordsScanned,
    String resultMessage,
    boolean fallbackUsed,
    String createdBy,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt) {}
