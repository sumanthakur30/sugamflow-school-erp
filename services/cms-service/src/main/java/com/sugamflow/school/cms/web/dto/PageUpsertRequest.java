package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PageUpsertRequest(
    @NotBlank String slug,
    @NotBlank String title,
    String summary,
    String bodyHtml,
    String seoTitle,
    String seoDescription) {}
