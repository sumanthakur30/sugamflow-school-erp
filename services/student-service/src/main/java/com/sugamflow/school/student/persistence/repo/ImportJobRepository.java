package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.ImportJobEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJobEntity, UUID> {
  List<ImportJobEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  Optional<ImportJobEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
