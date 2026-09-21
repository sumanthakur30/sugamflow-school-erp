package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.LifecycleEventEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifecycleEventRepository extends JpaRepository<LifecycleEventEntity, UUID> {

  List<LifecycleEventEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  List<LifecycleEventEntity> findByOrganizationIdAndStudentIdOrderByCreatedAtDesc(
      String organizationId, UUID studentId);

  Optional<LifecycleEventEntity> findByOrganizationIdAndIdempotencyKey(
      String organizationId, String idempotencyKey);

  Optional<LifecycleEventEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
