package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.ClassroomItemEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomItemRepository extends JpaRepository<ClassroomItemEntity, UUID> {

  List<ClassroomItemEntity> findByOrganizationIdAndKindOrderByCreatedAtDesc(
      String organizationId, String kind);

  Optional<ClassroomItemEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
