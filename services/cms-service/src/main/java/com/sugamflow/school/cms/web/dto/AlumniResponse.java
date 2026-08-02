package com.sugamflow.school.cms.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AlumniResponse(
    UUID id,
    String slug,
    String fullName,
    Integer batchYear,
    String headline,
    String bioHtml,
    String photoUrl,
    String status,
    String branchId,
    Instant publishedAt) {}
