package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentFolderRequest(
    @NotBlank @Size(max = 160) String name,
    String description,
    Long parentId,
    @Size(max = 40) String visibility,
    @Size(max = 80) String complianceArea) {}
