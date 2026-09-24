package com.sugamflow.school.compliance.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ComplianceDocumentResponse(
    Long id,
    String organizationId,
    String branchId,
    String docType,
    String title,
    String referenceNo,
    String issuer,
    LocalDate issuedOn,
    LocalDate expiresOn,
    String status,
    String externalUrl,
    String fileName,
    String contentType,
    Long fileSize,
    boolean hasFile,
    String versionLabel,
    String notes,
    boolean active,
    Instant updatedAt) {}
