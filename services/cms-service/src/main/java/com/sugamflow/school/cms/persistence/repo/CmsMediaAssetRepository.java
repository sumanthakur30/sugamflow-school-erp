package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsMediaAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CmsMediaAssetRepository extends JpaRepository<CmsMediaAsset, UUID> {

  List<CmsMediaAsset> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  Optional<CmsMediaAsset> findByIdAndOrganizationId(UUID id, String organizationId);

  @Query(
      "select coalesce(sum(m.byteSize), 0) from CmsMediaAsset m where m.organizationId = :org")
  long sumBytesByOrganizationId(@Param("org") String organizationId);

  long countByOrganizationId(String organizationId);
}
