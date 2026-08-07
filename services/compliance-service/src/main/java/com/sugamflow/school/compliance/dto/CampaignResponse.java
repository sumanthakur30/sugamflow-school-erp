package com.sugamflow.school.compliance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CampaignResponse(
    Long id,
    String organizationId,
    String academicSessionId,
    String boardCode,
    String packKey,
    String title,
    String status,
    Instant dueAt,
    BigDecimal complianceScore,
    int blockerCount,
    int warnCount,
    boolean requireManagementApproval,
    Instant lockedAt,
    String lockedBy,
    Instant submittedAt,
    String submittedBy,
    Instant archivedAt,
    String archivedBy,
    String adapterChannel,
    Instant createdAt,
    Instant updatedAt,
    List<ApprovalStepResponse> approvals,
    List<ExportArtifactResponse> artifacts) {

  public record ApprovalStepResponse(
      Long id,
      String stepCode,
      String decision,
      String actorUserId,
      String commentText,
      Instant decidedAt,
      Instant createdAt) {}

  public record ExportArtifactResponse(
      Long id,
      String artifactKey,
      String formatCode,
      String fileName,
      String contentType,
      long fileSize,
      String checksumSha256,
      Instant createdAt) {}
}
