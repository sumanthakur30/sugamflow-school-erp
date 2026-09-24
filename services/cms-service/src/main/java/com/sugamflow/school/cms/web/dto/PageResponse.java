package com.sugamflow.school.cms.web.dto;

import java.time.Instant;
import java.util.UUID;

public record PageResponse(
    UUID id,
    String slug,
    String title,
    String summary,
    String bodyHtml,
    String status,
    String seoTitle,
    String seoDescription,
    Instant publishedAt,
    Instant updatedAt) {}
