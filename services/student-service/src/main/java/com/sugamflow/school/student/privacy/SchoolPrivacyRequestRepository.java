package com.sugamflow.school.student.privacy;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolPrivacyRequestRepository extends JpaRepository<SchoolPrivacyRequestEntity, UUID> {

  List<SchoolPrivacyRequestEntity> findByOrganizationIdAndSubjectRefOrderByCreatedAtDesc(
      String organizationId, String subjectRef);

  List<SchoolPrivacyRequestEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  boolean existsByOrganizationIdAndDataCategoryAndSubjectLabelAndRequestTypeAndStatusIn(
      String organizationId,
      String dataCategory,
      String subjectLabel,
      String requestType,
      Collection<String> statuses);
}
