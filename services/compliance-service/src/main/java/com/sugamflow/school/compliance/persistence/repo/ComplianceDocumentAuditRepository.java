package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentAuditEntity;

public interface ComplianceDocumentAuditRepository
    extends JpaRepository<ComplianceDocumentAuditEntity, Long> {
  List<ComplianceDocumentAuditEntity> findByDocumentIdAndOrganizationIdOrderByCreatedAtDesc(
      Long documentId, String organizationId);
}
