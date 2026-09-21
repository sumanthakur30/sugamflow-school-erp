package com.sugamflow.school.compliance.persistence.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentEntity;

public interface ComplianceDocumentRepository extends JpaRepository<ComplianceDocumentEntity, Long> {
  List<ComplianceDocumentEntity> findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(
      String organizationId);

  List<ComplianceDocumentEntity> findByOrganizationIdAndDocTypeAndActiveTrueOrderByUpdatedAtDesc(
      String organizationId, String docType);

  Optional<ComplianceDocumentEntity> findByIdAndOrganizationId(Long id, String organizationId);

  long countByOrganizationIdAndActiveTrue(String organizationId);

  long countByOrganizationIdAndActiveTrueAndStatus(String organizationId, String status);

  List<ComplianceDocumentEntity>
      findByOrganizationIdAndActiveTrueAndExpiresOnNotNullAndExpiresOnLessThanEqualOrderByExpiresOnAsc(
          String organizationId, LocalDate onOrBefore);
}
