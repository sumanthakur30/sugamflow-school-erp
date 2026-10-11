package com.sugamflow.school.student.privacy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolPrivacyConsentRepository extends JpaRepository<SchoolPrivacyConsentEntity, UUID> {

  Optional<SchoolPrivacyConsentEntity> findByOrganizationIdAndSubjectRefAndPurposeCode(
      String organizationId, String subjectRef, String purposeCode);
}
