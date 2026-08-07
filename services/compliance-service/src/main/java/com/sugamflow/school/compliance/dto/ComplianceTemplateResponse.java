package com.sugamflow.school.compliance.dto;

import java.time.Instant;

import com.sugamflow.school.compliance.persistence.entity.ComplianceTemplateEntity;

public record ComplianceTemplateResponse(
    Long id,
    String packKey,
    String boardCode,
    String versionLabel,
    String title,
    String description,
    String status,
    boolean active,
    Instant publishedAt,
    Instant createdAt,
    Instant updatedAt) {

  public static ComplianceTemplateResponse from(ComplianceTemplateEntity e) {
    return new ComplianceTemplateResponse(
        e.getId(),
        e.getPackKey(),
        e.getBoardCode(),
        e.getVersionLabel(),
        e.getTitle(),
        e.getDescription(),
        e.getStatus(),
        e.isActive(),
        e.getPublishedAt(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
