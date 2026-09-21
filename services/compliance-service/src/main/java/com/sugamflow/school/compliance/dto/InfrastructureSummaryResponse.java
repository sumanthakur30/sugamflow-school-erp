package com.sugamflow.school.compliance.dto;

import java.util.List;

public record InfrastructureSummaryResponse(
    long totalAssets,
    long totalQuantity,
    List<CategoryCount> byCategory,
    List<String> missingRecommendedCategories) {

  public record CategoryCount(String category, long assets, long quantity) {}
}
