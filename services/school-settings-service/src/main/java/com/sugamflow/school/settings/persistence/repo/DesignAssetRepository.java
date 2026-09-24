package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.DesignAssetEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignAssetRepository extends JpaRepository<DesignAssetEntity, UUID> {

  Optional<DesignAssetEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  List<DesignAssetEntity> findByOrganizationIdAndBranchIdAndAssetType(
      String organizationId, String branchId, String assetType);

  void deleteByOrganizationIdAndBranchIdAndAssetType(
      String organizationId, String branchId, String assetType);
}
