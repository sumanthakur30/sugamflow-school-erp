package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.OfflineSyncItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineSyncItemRepository extends JpaRepository<OfflineSyncItemEntity, String> {
  List<OfflineSyncItemEntity> findByBatchIdOrderByCreatedAtAsc(String batchId);

  List<OfflineSyncItemEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
}
