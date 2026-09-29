package com.sugamflow.school.compliance.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplianceDocumentRequest(
    @NotBlank @Size(max = 80) String docType,
    @NotBlank @Size(max = 255) String title,
    @Size(max = 120) String referenceNo,
    @Size(max = 255) String issuer,
    LocalDate issuedOn,
    LocalDate expiresOn,
    @Size(max = 1000) String externalUrl,
    @Size(max = 40) String versionLabel,
    @Size(max = 100) String branchId,
    String notes,
    @Size(max = 80) String categoryCode,
    @Size(max = 80) String complianceArea,
    Long folderId,
    @Size(max = 100) String academicSessionId,
    @Size(max = 40) String visibility,
    @Size(max = 500) String tags,
    String description,
    @Size(max = 80) String relatedEntity,
    @Size(max = 100) String relatedEntityId,
    Integer retentionYears) {}
