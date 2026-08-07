package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.AdapterJobEntity;

public interface AdapterJobRepository extends JpaRepository<AdapterJobEntity, Long> {
  List<AdapterJobEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  List<AdapterJobEntity> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);
}
