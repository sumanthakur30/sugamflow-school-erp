package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCampaignRequest(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 40) String boardCode,
    @Size(max = 80) String packKey,
    @Size(max = 100) String academicSessionId,
    Boolean requireManagementApproval) {}
