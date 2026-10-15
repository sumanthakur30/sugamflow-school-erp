package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentVersionEntity;

public interface ComplianceDocumentVersionRepository
    extends JpaRepository<ComplianceDocumentVersionEntity, Long> {
  List<ComplianceDocumentVersionEntity> findByDocumentIdAndOrganizationIdOrderByVersionNoDesc(
      Long documentId, String organizationId);

  Optional<ComplianceDocumentVersionEntity> findByDocumentIdAndOrganizationIdAndVersionNo(
      Long documentId, String organizationId, int versionNo);
}
