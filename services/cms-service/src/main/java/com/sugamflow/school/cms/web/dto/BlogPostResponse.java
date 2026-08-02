package com.sugamflow.school.cms.web.dto;

import java.time.Instant;
import java.util.UUID;

public record BlogPostResponse(
    UUID id,
    String slug,
    String title,
    String summary,
    String bodyHtml,
    String coverImageUrl,
    String status,
    Instant publishedAt,
    Instant updatedAt) {}
