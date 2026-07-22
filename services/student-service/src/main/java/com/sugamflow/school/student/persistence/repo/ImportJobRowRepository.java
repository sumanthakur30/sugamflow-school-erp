package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.ImportJobRowEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRowRepository extends JpaRepository<ImportJobRowEntity, UUID> {
  List<ImportJobRowEntity> findByJobIdOrderByRowNumberAsc(UUID jobId);

  List<ImportJobRowEntity> findByJobIdAndStatusOrderByRowNumberAsc(UUID jobId, String status);

  long countByJobIdAndStatus(UUID jobId, String status);
}
