package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.BiometricCommandEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiometricCommandRepository extends JpaRepository<BiometricCommandEntity, UUID> {

  List<BiometricCommandEntity> findByDeviceIdAndStatusOrderByRequestedAtAsc(String deviceId, String status);

  List<BiometricCommandEntity> findTop50ByOrganizationIdAndDeviceIdOrderByRequestedAtDesc(
      String organizationId, String deviceId);
}
