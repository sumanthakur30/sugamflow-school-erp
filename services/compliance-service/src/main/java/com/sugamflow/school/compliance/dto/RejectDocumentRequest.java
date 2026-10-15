package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectDocumentRequest(@NotBlank String reason) {}