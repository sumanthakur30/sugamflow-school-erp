package com.sugamflow.school.admission.persistence.repo;

import com.sugamflow.school.admission.persistence.entity.AdmissionApplicationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplicationEntity, UUID> {

  List<AdmissionApplicationEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<AdmissionApplicationEntity> findByOrganizationIdOrderByUpdatedAtDesc(
      String organizationId, Pageable pageable);

  Page<AdmissionApplicationEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  Optional<AdmissionApplicationEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
