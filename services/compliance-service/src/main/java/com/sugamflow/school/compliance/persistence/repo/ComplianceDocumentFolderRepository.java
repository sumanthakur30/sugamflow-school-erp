package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentFolderEntity;

public interface ComplianceDocumentFolderRepository
    extends JpaRepository<ComplianceDocumentFolderEntity, Long> {
  List<ComplianceDocumentFolderEntity> findByOrganizationIdAndActiveTrueOrderByNameAsc(
      String organizationId);

  Optional<ComplianceDocumentFolderEntity> findByIdAndOrganizationId(Long id, String organizationId);
}
