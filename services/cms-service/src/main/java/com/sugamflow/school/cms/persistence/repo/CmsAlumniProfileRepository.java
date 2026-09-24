package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsAlumniProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsAlumniProfileRepository extends JpaRepository<CmsAlumniProfile, UUID> {

  List<CmsAlumniProfile> findByOrganizationIdAndStatusOrderByBatchYearDescFullNameAsc(
      String organizationId, String status);

  Optional<CmsAlumniProfile> findByOrganizationIdAndSlugAndStatus(
      String organizationId, String slug, String status);

  List<CmsAlumniProfile> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Optional<CmsAlumniProfile> findByOrganizationIdAndSlug(String organizationId, String slug);

  Optional<CmsAlumniProfile> findByIdAndOrganizationId(UUID id, String organizationId);
}
