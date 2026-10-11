package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.StudentFeeAdjustmentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentFeeAdjustmentRepository extends JpaRepository<StudentFeeAdjustmentEntity, String> {

  List<StudentFeeAdjustmentEntity> findByOrganizationIdAndAdmissionNoOrderByCreatedAtDesc(
      String organizationId, String admissionNo);

  List<StudentFeeAdjustmentEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  Optional<StudentFeeAdjustmentEntity>
      findFirstByOrganizationIdAndAdmissionNoAndAcademicSessionIdAndStatusOrderByCreatedAtDesc(
          String organizationId, String admissionNo, String academicSessionId, String status);
}
