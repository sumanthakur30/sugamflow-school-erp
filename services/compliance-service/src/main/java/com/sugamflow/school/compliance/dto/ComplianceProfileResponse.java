package com.sugamflow.school.compliance.dto;

import java.time.Instant;

import com.sugamflow.school.compliance.persistence.entity.SchoolComplianceProfileEntity;

public record ComplianceProfileResponse(
    Long id,
    String organizationId,
    String boardCode,
    String activePackKey,
    String schoolName,
    String affiliationNumber,
    String schoolCode,
    String udisePlus,
    String diseCode,
    String addressLine,
    String city,
    String stateCode,
    String pincode,
    String principalName,
    String principalMobile,
    String principalEmail,
    String schoolPhone,
    String schoolEmail,
    String bankAccountName,
    String bankAccountNumber,
    String bankIfsc,
    String trustSocietyName,
    String recognitionDetails,
    String infrastructureNotes,
    Instant createdAt,
    Instant updatedAt,
    int profileCompletenessPercent) {

  public static ComplianceProfileResponse from(SchoolComplianceProfileEntity e, int completeness) {
    return new ComplianceProfileResponse(
        e.getId(),
        e.getOrganizationId(),
        e.getBoardCode(),
        e.getActivePackKey(),
        e.getSchoolName(),
        e.getAffiliationNumber(),
        e.getSchoolCode(),
        e.getUdisePlus(),
        e.getDiseCode(),
        e.getAddressLine(),
        e.getCity(),
        e.getStateCode(),
        e.getPincode(),
        e.getPrincipalName(),
        e.getPrincipalMobile(),
        e.getPrincipalEmail(),
        e.getSchoolPhone(),
        e.getSchoolEmail(),
        e.getBankAccountName(),
        e.getBankAccountNumber(),
        e.getBankIfsc(),
        e.getTrustSocietyName(),
        e.getRecognitionDetails(),
        e.getInfrastructureNotes(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        completeness);
  }
}
