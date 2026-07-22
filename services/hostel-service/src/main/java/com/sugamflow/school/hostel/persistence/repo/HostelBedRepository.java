package com.sugamflow.school.hostel.persistence.repo;

import com.sugamflow.school.hostel.persistence.entity.HostelBedEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostelBedRepository extends JpaRepository<HostelBedEntity, UUID> {
  List<HostelBedEntity> findByOrganizationIdOrderByBlockKeyAscRoomNoAscBedNoAsc(String organizationId);

  Optional<HostelBedEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
