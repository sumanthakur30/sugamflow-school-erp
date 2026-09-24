package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsDocumentRepository extends JpaRepository<CmsDocument, UUID> {
  List<CmsDocument> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  List<CmsDocument> findByOrganizationIdAndStatusOrderByPublishedAtDesc(
      String organizationId, String status);

  Optional<CmsDocument> findByIdAndOrganizationId(UUID id, String organizationId);
}
