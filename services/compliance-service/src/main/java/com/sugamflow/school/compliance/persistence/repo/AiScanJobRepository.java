package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.AiScanJobEntity;

public interface AiScanJobRepository extends JpaRepository<AiScanJobEntity, Long> {
  List<AiScanJobEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  Optional<AiScanJobEntity> findByIdAndOrganizationId(Long id, String organizationId);

  List<AiScanJobEntity> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
