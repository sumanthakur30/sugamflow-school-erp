package com.sugamflow.school.notification.persistence.repo;

import com.sugamflow.school.notification.persistence.entity.CommsAnnouncementEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommsAnnouncementRepository extends JpaRepository<CommsAnnouncementEntity, UUID> {
  List<CommsAnnouncementEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  @Query(
      """
      SELECT a FROM CommsAnnouncementEntity a
      WHERE a.status IN ('QUEUED', 'DISPATCHING')
      ORDER BY a.createdAt ASC
      """)
  List<CommsAnnouncementEntity> findDispatchable(Pageable pageable);
}
