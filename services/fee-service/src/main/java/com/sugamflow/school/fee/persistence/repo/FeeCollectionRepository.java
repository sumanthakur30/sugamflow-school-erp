package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.FeeCollectionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeCollectionRepository extends JpaRepository<FeeCollectionEntity, UUID> {

  List<FeeCollectionEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<FeeCollectionEntity> findByOrganizationIdOrderByUpdatedAtDesc(
      String organizationId, Pageable pageable);

  Page<FeeCollectionEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  List<FeeCollectionEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId);

  Optional<FeeCollectionEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
