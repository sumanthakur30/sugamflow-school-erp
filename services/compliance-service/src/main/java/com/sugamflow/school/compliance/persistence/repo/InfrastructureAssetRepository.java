package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.InfrastructureAssetEntity;

public interface InfrastructureAssetRepository extends JpaRepository<InfrastructureAssetEntity, Long> {
  List<InfrastructureAssetEntity> findByOrganizationIdAndActiveTrueOrderByCategoryAscNameAsc(
      String organizationId);

  List<InfrastructureAssetEntity> findByOrganizationIdAndCategoryAndActiveTrueOrderByNameAsc(
      String organizationId, String category);

  Optional<InfrastructureAssetEntity> findByIdAndOrganizationId(Long id, String organizationId);

  long countByOrganizationIdAndActiveTrue(String organizationId);
}
