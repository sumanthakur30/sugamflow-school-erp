package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.Size;

public record FieldMapUpdateRequest(
    @Size(max = 160) String label,
    Boolean required,
    @Size(max = 20) String severity,
    @Size(max = 255) String formatRegex,
    Integer sortOrder,
    Boolean active) {}
