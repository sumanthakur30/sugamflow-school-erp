package com.sugamflow.school.hostel.persistence.repo;

import com.sugamflow.school.hostel.persistence.entity.HostelRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostelRecordRepository extends JpaRepository<HostelRecordEntity, UUID> {

  List<HostelRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<HostelRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId, Pageable pageable);
  Page<HostelRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  Optional<HostelRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
