package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record NewsUpsertRequest(
    @NotBlank String slug,
    @NotBlank String title,
    String summary,
    String bodyHtml,
    String coverImageUrl,
    String category,
    Integer priority,
    String audience,
    Instant expiresAt) {}
