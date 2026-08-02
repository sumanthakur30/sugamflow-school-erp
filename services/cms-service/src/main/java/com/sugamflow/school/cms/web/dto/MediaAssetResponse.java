package com.sugamflow.school.cms.web.dto;

import java.time.Instant;
import java.util.UUID;

public record MediaAssetResponse(
    UUID id,
    String fileName,
    String contentType,
    String url,
    Long byteSize,
    Instant createdAt) {}
