package com.sugamflow.school.compliance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ComplianceDashboardResponse(
    int complianceScore,
    long pendingCampaigns,
    long submittedCampaigns,
    long overdueCampaigns,
    int profileCompletenessPercent,
    int missingProfileFields,
    List<String> principalActionItems,
    List<CampaignSummary> recentCampaigns) {

  public record CampaignSummary(
      Long id,
      String title,
      String boardCode,
      String status,
      Instant dueAt,
      BigDecimal complianceScore,
      int blockerCount,
      int warnCount) {}
}
