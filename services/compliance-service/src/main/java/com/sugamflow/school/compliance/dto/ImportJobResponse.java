package com.sugamflow.school.compliance.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.sugamflow.school.compliance.persistence.entity.ComplianceImportJobEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceImportJobRowEntity;

public record ImportJobResponse(
    Long id,
    String organizationId,
    String boardCode,
    String packKey,
    String entityType,
    String fileName,
    String status,
    boolean fillBlankOnly,
    int totalRows,
    int readyCount,
    int errorCount,
    int matchedCount,
    int updatedCount,
    String createdBy,
    Instant createdAt,
    Instant updatedAt,
    List<ImportJobRowResponse> rows) {

  public record ImportJobRowResponse(
      Long id,
      int rowNo,
      String matchKey,
      String entityId,
      String status,
      Map<String, Object> payload,
      String errorMessage) {

    public static ImportJobRowResponse from(ComplianceImportJobRowEntity e) {
      return new ImportJobRowResponse(
          e.getId(),
          e.getRowNo(),
          e.getMatchKey(),
          e.getEntityId(),
          e.getStatus(),
          e.getPayloadJson(),
          e.getErrorMessage());
    }
  }

  public static ImportJobResponse from(ComplianceImportJobEntity e) {
    return from(e, List.of());
  }

  public static ImportJobResponse from(
      ComplianceImportJobEntity e, List<ComplianceImportJobRowEntity> rows) {
    return new ImportJobResponse(
        e.getId(),
        e.getOrganizationId(),
        e.getBoardCode(),
        e.getPackKey(),
        e.getEntityType(),
        e.getFileName(),
        e.getStatus(),
        e.isFillBlankOnly(),
        e.getTotalRows(),
        e.getReadyCount(),
        e.getErrorCount(),
        e.getMatchedCount(),
        e.getUpdatedCount(),
        e.getCreatedBy(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        rows.stream().map(ImportJobRowResponse::from).toList());
  }
}
