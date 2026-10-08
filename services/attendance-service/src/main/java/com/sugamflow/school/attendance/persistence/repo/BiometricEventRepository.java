package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.BiometricEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiometricEventRepository extends JpaRepository<BiometricEventEntity, UUID> {

  Optional<BiometricEventEntity> findByEventHash(String eventHash);

  Optional<BiometricEventEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  List<BiometricEventEntity> findTop200ByOrganizationIdOrderByReceivedAtDesc(String organizationId);

  List<BiometricEventEntity> findTop100ByOrganizationIdAndStatusOrderByReceivedAtDesc(
      String organizationId, String status);

  List<BiometricEventEntity> findByOrganizationIdAndEventTimeBetweenOrderByEventTimeDesc(
      String organizationId, Instant from, Instant to);

  long countByDeviceIdAndReceivedAtAfter(String deviceId, Instant after);

  long countByDeviceIdAndStatus(String deviceId, String status);

  long countByOrganizationIdAndStatusAndReceivedAtAfter(String organizationId, String status, Instant after);
}
