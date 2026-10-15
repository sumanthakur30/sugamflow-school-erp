package com.sugamflow.school.compliance.dto;

import java.time.Instant;

public record DocumentVersionResponse(
    Long id,
    int versionNo,
    String fileName,
    String contentType,
    Long fileSize,
    String changeReason,
    String uploadedBy,
    Instant uploadedAt,
    boolean current) {}
