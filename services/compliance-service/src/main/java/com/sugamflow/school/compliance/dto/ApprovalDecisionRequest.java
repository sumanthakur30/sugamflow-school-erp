package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
    @NotBlank @Size(max = 40) String stepCode,
    @NotBlank @Size(max = 40) String decision,
    @Size(max = 2000) String comment) {}
