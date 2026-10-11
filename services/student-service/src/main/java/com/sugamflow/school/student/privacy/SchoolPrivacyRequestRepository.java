package com.sugamflow.school.student.privacy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolPrivacyRequestRepository extends JpaRepository<SchoolPrivacyRequestEntity, UUID> {

  List<SchoolPrivacyRequestEntity> findByOrganizationIdAndSubjectRefOrderByCreatedAtDesc(
      String organizationId, String subjectRef);

  List<SchoolPrivacyRequestEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
}
