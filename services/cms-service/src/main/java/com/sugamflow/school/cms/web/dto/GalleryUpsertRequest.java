package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;

public record GalleryUpsertRequest(
    @NotBlank String title,
    String caption,
    @NotBlank String imageUrl,
    String album,
    Integer sortOrder) {}
