package com.sugamflow.school.cms.web.dto;

import java.time.Instant;
import java.util.UUID;

public record NewsResponse(
    UUID id,
    String slug,
    String title,
    String summary,
    String bodyHtml,
    String coverImageUrl,
    Instant publishedAt) {}
