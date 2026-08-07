package com.sugamflow.school.support.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSupportCommentRequest(
    @NotBlank @Size(max = 10000) String body, boolean internalNote) {}
