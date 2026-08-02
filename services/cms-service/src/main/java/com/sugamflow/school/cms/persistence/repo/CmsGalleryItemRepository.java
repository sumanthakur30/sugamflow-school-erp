package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsGalleryItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsGalleryItemRepository extends JpaRepository<CmsGalleryItem, UUID> {
  List<CmsGalleryItem> findByOrganizationIdAndStatusOrderByAlbumAscSortOrderAsc(
      String organizationId, String status);

  List<CmsGalleryItem> findByOrganizationIdOrderByAlbumAscSortOrderAsc(String organizationId);

  Optional<CmsGalleryItem> findByIdAndOrganizationId(UUID id, String organizationId);
}
