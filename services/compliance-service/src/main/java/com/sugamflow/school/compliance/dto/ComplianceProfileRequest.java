package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.Size;

public record ComplianceProfileRequest(
    @Size(max = 40) String boardCode,
    @Size(max = 80) String activePackKey,
    @Size(max = 255) String schoolName,
    @Size(max = 80) String affiliationNumber,
    @Size(max = 80) String schoolCode,
    @Size(max = 80) String udisePlus,
    @Size(max = 80) String diseCode,
    String addressLine,
    @Size(max = 120) String city,
    @Size(max = 40) String stateCode,
    @Size(max = 20) String pincode,
    @Size(max = 160) String principalName,
    @Size(max = 30) String principalMobile,
    @Size(max = 160) String principalEmail,
    @Size(max = 30) String schoolPhone,
    @Size(max = 160) String schoolEmail,
    @Size(max = 160) String bankAccountName,
    @Size(max = 64) String bankAccountNumber,
    @Size(max = 20) String bankIfsc,
    @Size(max = 255) String trustSocietyName,
    String recognitionDetails,
    String infrastructureNotes) {}
