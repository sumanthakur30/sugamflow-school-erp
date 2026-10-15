package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.CampusDeskItemEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusDeskItemRepository extends JpaRepository<CampusDeskItemEntity, UUID> {

  List<CampusDeskItemEntity> findByOrganizationIdAndKindOrderByCreatedAtDesc(
      String organizationId, String kind);

  Optional<CampusDeskItemEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
