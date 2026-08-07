package com.sugamflow.school.compliance.dto;

import java.util.List;

public record DocumentVaultSummaryResponse(
    long totalDocuments,
    long validCount,
    long expiringCount,
    long expiredCount,
    List<String> missingRecommendedTypes,
    List<ComplianceDocumentResponse> expiringSoon) {}
