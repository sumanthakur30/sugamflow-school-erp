package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;

public interface SubmissionCampaignRepository
    extends JpaRepository<SubmissionCampaignEntity, Long> {
  List<SubmissionCampaignEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  long countByOrganizationIdAndStatus(String organizationId, String status);

  Optional<SubmissionCampaignEntity> findByIdAndOrganizationId(Long id, String organizationId);
}
