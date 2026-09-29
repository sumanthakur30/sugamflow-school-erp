package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentCategoryEntity;

public interface ComplianceDocumentCategoryRepository
    extends JpaRepository<ComplianceDocumentCategoryEntity, Long> {
  List<ComplianceDocumentCategoryEntity> findByOrganizationIdAndActiveTrueOrderBySortOrderAscNameAsc(
      String organizationId);

  Optional<ComplianceDocumentCategoryEntity> findByOrganizationIdAndCode(
      String organizationId, String code);

  long countByOrganizationId(String organizationId);
}
