package com.sugamflow.school.cms.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AlumniUpsertRequest(
    @NotBlank String slug,
    @NotBlank String fullName,
    Integer batchYear,
    String headline,
    String bioHtml,
    String photoUrl,
    String branchId) {}
