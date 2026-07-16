package com.sugamflow.school.payroll.persistence.repo;

import com.sugamflow.school.payroll.persistence.entity.PayrollRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecordEntity, UUID> {

  List<PayrollRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<PayrollRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId, Pageable pageable);
  Page<PayrollRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  Optional<PayrollRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
