package com.sugamflow.school.cms.web.dto;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
    UUID id,
    String slug,
    String title,
    String summary,
    String bodyHtml,
    String locationText,
    Instant startsAt,
    Instant endsAt) {}
