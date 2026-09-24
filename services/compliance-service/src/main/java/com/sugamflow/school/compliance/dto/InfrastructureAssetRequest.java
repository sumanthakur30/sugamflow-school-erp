package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InfrastructureAssetRequest(
    @NotBlank @Size(max = 60) String category,
    @NotBlank @Size(max = 255) String name,
    @Min(0) Integer quantity,
    Integer capacity,
    @Size(max = 40) String unitLabel,
    @Size(max = 40) String conditionCode,
    @Size(max = 100) String branchId,
    @Size(max = 255) String locationNote,
    String notes) {}
