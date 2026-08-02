package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsPage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsPageRepository extends JpaRepository<CmsPage, UUID> {
  Optional<CmsPage> findByOrganizationIdAndSlug(String organizationId, String slug);

  List<CmsPage> findByOrganizationIdAndStatusOrderBySlugAsc(String organizationId, String status);

  Optional<CmsPage> findByOrganizationIdAndSlugAndStatus(
      String organizationId, String slug, String status);

  List<CmsPage> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Optional<CmsPage> findByIdAndOrganizationId(UUID id, String organizationId);
}
