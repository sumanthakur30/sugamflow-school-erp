package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;

public record BlogUpsertRequest(
    @NotBlank String slug,
    @NotBlank String title,
    String summary,
    String bodyHtml,
    String coverImageUrl) {}
