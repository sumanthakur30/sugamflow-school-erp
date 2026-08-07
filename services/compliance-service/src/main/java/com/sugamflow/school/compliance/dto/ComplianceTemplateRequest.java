package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplianceTemplateRequest(
    @NotBlank @Size(max = 80) String packKey,
    @NotBlank @Size(max = 40) String boardCode,
    @NotBlank @Size(max = 40) String versionLabel,
    @NotBlank @Size(max = 255) String title,
    String description,
    @Size(max = 40) String status,
    Boolean active) {}
