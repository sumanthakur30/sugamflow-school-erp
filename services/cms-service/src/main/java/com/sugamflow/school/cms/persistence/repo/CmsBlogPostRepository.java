package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsBlogPost;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsBlogPostRepository extends JpaRepository<CmsBlogPost, UUID> {

  List<CmsBlogPost> findByOrganizationIdAndStatusOrderByPublishedAtDesc(
      String organizationId, String status);

  Optional<CmsBlogPost> findByOrganizationIdAndSlugAndStatus(
      String organizationId, String slug, String status);

  List<CmsBlogPost> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Optional<CmsBlogPost> findByOrganizationIdAndSlug(String organizationId, String slug);

  Optional<CmsBlogPost> findByIdAndOrganizationId(UUID id, String organizationId);
}
