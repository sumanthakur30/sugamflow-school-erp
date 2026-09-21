package com.sugamflow.school.transport.persistence.repo;

import com.sugamflow.school.transport.persistence.entity.TransportRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportRecordRepository extends JpaRepository<TransportRecordEntity, UUID> {

  List<TransportRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<TransportRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId, Pageable pageable);
  Page<TransportRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  Optional<TransportRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
