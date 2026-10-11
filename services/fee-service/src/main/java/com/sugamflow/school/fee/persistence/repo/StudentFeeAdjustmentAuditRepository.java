package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.StudentFeeAdjustmentAuditEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentFeeAdjustmentAuditRepository
    extends JpaRepository<StudentFeeAdjustmentAuditEntity, String> {

  List<StudentFeeAdjustmentAuditEntity> findByOrganizationIdAndAdjustmentIdOrderByCreatedAtAsc(
      String organizationId, String adjustmentId);
}
