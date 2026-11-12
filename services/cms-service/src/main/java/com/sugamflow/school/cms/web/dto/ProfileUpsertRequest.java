package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ProfileUpsertRequest(
    @NotBlank String fullName,
    String role,
    String photoUrl,
    UUID mediaAssetId,
    Integer sortOrder) {}
