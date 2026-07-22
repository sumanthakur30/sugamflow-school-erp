package com.sugamflow.school.hostel.persistence.repo;

import com.sugamflow.school.hostel.persistence.entity.HostelOccupancyEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostelOccupancyRepository extends JpaRepository<HostelOccupancyEntity, UUID> {
  List<HostelOccupancyEntity> findByOrganizationIdAndStatusOrderByAllocatedAtDesc(
      String organizationId, String status);

  Optional<HostelOccupancyEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Optional<HostelOccupancyEntity> findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
      String organizationId, String admissionNo, String status);
}
