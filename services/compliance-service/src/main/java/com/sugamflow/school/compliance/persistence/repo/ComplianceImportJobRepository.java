package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceImportJobEntity;

public interface ComplianceImportJobRepository
    extends JpaRepository<ComplianceImportJobEntity, Long> {
  List<ComplianceImportJobEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
}
