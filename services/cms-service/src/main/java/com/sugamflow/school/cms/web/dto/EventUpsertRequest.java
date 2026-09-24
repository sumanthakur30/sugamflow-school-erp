package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record EventUpsertRequest(
    @NotBlank String slug,
    @NotBlank String title,
    String summary,
    String bodyHtml,
    String locationText,
    @NotNull Instant startsAt,
    Instant endsAt,
    String category,
    Integer priority,
    String audience) {}
