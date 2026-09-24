package com.sugamflow.school.compliance.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DisclosurePackageResponse(
    String organizationId,
    String slug,
    String title,
    String summary,
    String bodyHtml,
    Map<String, Object> snapshot,
    List<String> warnings,
    Instant generatedAt,
    Instant lastPublishedAt,
    String lastPublishStatus,
    String cmsPageId,
    String publicUrlHint) {}
