package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record MediaRegisterRequest(
    @NotBlank String fileName,
    String contentType,
    @NotBlank String url,
    @PositiveOrZero Long byteSize) {}
