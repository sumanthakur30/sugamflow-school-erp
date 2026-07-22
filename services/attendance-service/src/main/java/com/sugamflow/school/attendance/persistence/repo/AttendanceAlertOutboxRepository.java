package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.AttendanceAlertOutboxEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceAlertOutboxRepository
    extends JpaRepository<AttendanceAlertOutboxEntity, UUID> {

  List<AttendanceAlertOutboxEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

  List<AttendanceAlertOutboxEntity> findByMarkIdAndMarkStatus(UUID markId, String markStatus);

  Optional<AttendanceAlertOutboxEntity> findByMarkIdAndMarkStatusAndChannelAndRecipient(
      UUID markId, String markStatus, String channel, String recipient);

  @Query(
      """
      SELECT o FROM AttendanceAlertOutboxEntity o
      WHERE o.status IN ('PENDING', 'FAILED')
        AND o.attempts < :maxAttempts
        AND o.createdAt >= :notBefore
      ORDER BY o.createdAt ASC
      """)
  List<AttendanceAlertOutboxEntity> findRetryable(
      @Param("maxAttempts") int maxAttempts,
      @Param("notBefore") Instant notBefore,
      Pageable pageable);
}
