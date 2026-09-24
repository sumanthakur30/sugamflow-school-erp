package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.HomeworkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeworkRepository extends JpaRepository<HomeworkEntity, UUID> {

  List<HomeworkEntity> findByOrganizationIdOrderByDueAtAscCreatedAtDesc(String organizationId);

  List<HomeworkEntity> findByOrganizationIdAndStatusOrderByDueAtAscCreatedAtDesc(
      String organizationId, String status);

  Optional<HomeworkEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
