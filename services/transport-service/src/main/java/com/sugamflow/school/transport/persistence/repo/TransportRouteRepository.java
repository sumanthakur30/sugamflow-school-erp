package com.sugamflow.school.transport.persistence.repo;

import com.sugamflow.school.transport.persistence.entity.TransportRouteEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportRouteRepository extends JpaRepository<TransportRouteEntity, UUID> {
  List<TransportRouteEntity> findByOrganizationIdOrderByRouteNameAsc(String organizationId);

  Optional<TransportRouteEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
