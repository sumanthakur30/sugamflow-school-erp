package com.sugamflow.school.compliance.dto;

import java.time.Instant;

public record AdapterJobResponse(
    Long id,
    Long campaignId,
    String boardCode,
    String channel,
    String status,
    String externalRef,
    String requestNote,
    String resultMessage,
    int artifactCount,
    String createdBy,
    Instant createdAt,
    Instant completedAt) {}
