package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.OfflineSyncBatchEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineSyncBatchRepository extends JpaRepository<OfflineSyncBatchEntity, String> {
  List<OfflineSyncBatchEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
}
