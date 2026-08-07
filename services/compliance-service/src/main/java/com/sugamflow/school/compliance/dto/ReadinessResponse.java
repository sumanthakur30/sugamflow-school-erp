package com.sugamflow.school.compliance.dto;

import java.util.List;

public record ReadinessResponse(
    int openStudentFindings,
    int openStaffFindings,
    int openBlockers,
    int openWarnings,
    int readinessPercent,
    List<GapRow> gaps,
    Long latestCampaignId,
    String latestCampaignStatus) {

  public record GapRow(
      String entityType, String fieldKey, String severity, long count, String label) {}
}
