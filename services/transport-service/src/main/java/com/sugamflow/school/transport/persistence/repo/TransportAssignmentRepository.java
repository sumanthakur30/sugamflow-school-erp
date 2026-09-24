package com.sugamflow.school.transport.persistence.repo;

import com.sugamflow.school.transport.persistence.entity.TransportAssignmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportAssignmentRepository
    extends JpaRepository<TransportAssignmentEntity, UUID> {
  List<TransportAssignmentEntity> findByOrganizationIdAndStatusOrderByAssignedAtDesc(
      String organizationId, String status);

  long countByOrganizationIdAndRouteIdAndStatus(
      String organizationId, UUID routeId, String status);

  Optional<TransportAssignmentEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Optional<TransportAssignmentEntity> findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
      String organizationId, String admissionNo, String status);
}
