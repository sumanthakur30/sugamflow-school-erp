package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ExportArtifactEntity;

public interface ExportArtifactRepository extends JpaRepository<ExportArtifactEntity, Long> {
  List<ExportArtifactEntity> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);

  Optional<ExportArtifactEntity> findByIdAndOrganizationId(Long id, String organizationId);

  void deleteByCampaignId(Long campaignId);
}
