package com.sugamflow.school.cms.web.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    String title,
    String category,
    String summary,
    String fileUrl,
    String fileName,
    String status,
    String audience,
    Instant publishedAt,
    Instant expiresAt) {}
