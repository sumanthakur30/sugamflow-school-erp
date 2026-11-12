package com.sugamflow.school.cms.web.dto;

import java.util.UUID;

public record ProfileResponse(
    UUID id,
    String fullName,
    String role,
    String photoUrl,
    UUID mediaAssetId,
    int sortOrder,
    String status) {}
