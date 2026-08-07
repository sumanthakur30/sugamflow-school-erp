package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;

public record NewsUpsertRequest(
    @NotBlank String slug,
    @NotBlank String title,
    String summary,
    String bodyHtml,
    String coverImageUrl) {}
