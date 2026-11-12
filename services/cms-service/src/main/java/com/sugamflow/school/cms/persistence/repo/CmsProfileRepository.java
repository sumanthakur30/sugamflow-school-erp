package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsProfileRepository extends JpaRepository<CmsProfile, UUID> {
  List<CmsProfile> findByOrganizationIdOrderBySortOrderAscFullNameAsc(String organizationId);

  Optional<CmsProfile> findByIdAndOrganizationId(UUID id, String organizationId);
}
