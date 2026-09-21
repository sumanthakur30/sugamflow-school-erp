package com.sugamflow.school.notification.persistence.repo;

import com.sugamflow.school.notification.persistence.entity.CommsAlertOutboxEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommsAlertOutboxRepository extends JpaRepository<CommsAlertOutboxEntity, UUID> {

  List<CommsAlertOutboxEntity> findByAnnouncementIdOrderByCreatedAtAsc(UUID announcementId);

  Optional<CommsAlertOutboxEntity> findByAnnouncementIdAndChannelAndRecipient(
      UUID announcementId, String channel, String recipient);

  @Query(
      """
      SELECT o FROM CommsAlertOutboxEntity o
      WHERE o.status IN ('PENDING', 'FAILED')
        AND o.attempts < :maxAttempts
        AND o.createdAt >= :notBefore
      ORDER BY o.createdAt ASC
      """)
  List<CommsAlertOutboxEntity> findRetryable(
      @Param("maxAttempts") int maxAttempts,
      @Param("notBefore") Instant notBefore,
      Pageable pageable);
}
