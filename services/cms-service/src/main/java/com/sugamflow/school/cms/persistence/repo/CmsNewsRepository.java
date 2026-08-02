package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsNews;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsNewsRepository extends JpaRepository<CmsNews, UUID> {
  List<CmsNews> findByOrganizationIdAndStatusOrderByPublishedAtDesc(
      String organizationId, String status);

  Optional<CmsNews> findByOrganizationIdAndSlugAndStatus(
      String organizationId, String slug, String status);

  List<CmsNews> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Optional<CmsNews> findByIdAndOrganizationId(UUID id, String organizationId);
}
