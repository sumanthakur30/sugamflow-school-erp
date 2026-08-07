package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.Size;

public record ComplianceTemplateUpdateRequest(
    @Size(max = 255) String title,
    String description,
    @Size(max = 40) String status,
    Boolean active) {}
