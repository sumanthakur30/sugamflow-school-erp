package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record DocumentUpsertRequest(
    @NotBlank String title,
    String category,
    String summary,
    @NotBlank String fileUrl,
    String fileName,
    String audience,
    Instant expiresAt,
    String academicYear,
    Integer sortOrder,
    String contentType,
    Long byteSize,
    UUID mediaAssetId) {}
