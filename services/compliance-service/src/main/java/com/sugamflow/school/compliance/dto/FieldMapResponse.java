package com.sugamflow.school.compliance.dto;

import com.sugamflow.school.compliance.persistence.entity.ComplianceFieldMapEntity;

public record FieldMapResponse(
    Long id,
    String boardCode,
    String entityType,
    String fieldKey,
    String sourcePath,
    String label,
    boolean required,
    String severity,
    String formatRegex,
    int sortOrder,
    boolean active) {

  public static FieldMapResponse from(ComplianceFieldMapEntity e) {
    return new FieldMapResponse(
        e.getId(),
        e.getBoardCode(),
        e.getEntityType(),
        e.getFieldKey(),
        e.getSourcePath(),
        e.getLabel(),
        e.isRequired(),
        e.getSeverity(),
        e.getFormatRegex(),
        e.getSortOrder(),
        e.isActive());
  }
}
