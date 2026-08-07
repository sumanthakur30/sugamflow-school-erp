package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ApprovalStepEntity;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStepEntity, Long> {
  List<ApprovalStepEntity> findByCampaignIdOrderByCreatedAtAsc(Long campaignId);

  Optional<ApprovalStepEntity> findByCampaignIdAndStepCode(Long campaignId, String stepCode);

  void deleteByCampaignId(Long campaignId);
}
