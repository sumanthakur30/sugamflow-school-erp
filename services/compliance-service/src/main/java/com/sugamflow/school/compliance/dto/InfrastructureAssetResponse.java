package com.sugamflow.school.compliance.dto;

import java.time.Instant;

public record InfrastructureAssetResponse(
    Long id,
    String organizationId,
    String branchId,
    String category,
    String name,
    int quantity,
    Integer capacity,
    String unitLabel,
    String conditionCode,
    String locationNote,
    String notes,
    boolean active,
    Instant updatedAt) {}
