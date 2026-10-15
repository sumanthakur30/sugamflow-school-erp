package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.BiometricEnrollmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiometricEnrollmentRepository extends JpaRepository<BiometricEnrollmentEntity, UUID> {

  List<BiometricEnrollmentEntity> findByOrganizationIdOrderByDisplayNameAsc(String organizationId);

  Optional<BiometricEnrollmentEntity> findByOrganizationIdAndEnrollmentCode(
      String organizationId, String enrollmentCode);

  Optional<BiometricEnrollmentEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
