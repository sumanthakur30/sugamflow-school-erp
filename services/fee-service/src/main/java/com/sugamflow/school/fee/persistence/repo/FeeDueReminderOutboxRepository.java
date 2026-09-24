package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.FeeDueReminderOutboxEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeDueReminderOutboxRepository
    extends JpaRepository<FeeDueReminderOutboxEntity, UUID> {

  Optional<FeeDueReminderOutboxEntity>
      findByOrganizationIdAndStudentKeyAndPeriodKeyAndChannelAndRecipient(
          String organizationId,
          String studentKey,
          String periodKey,
          String channel,
          String recipient);

  List<FeeDueReminderOutboxEntity> findByOrganizationIdAndAdmissionNoOrderByCreatedAtDesc(
      String organizationId, String admissionNo);

  List<FeeDueReminderOutboxEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  @Query(
      """
      SELECT o FROM FeeDueReminderOutboxEntity o
      WHERE o.status IN ('PENDING', 'FAILED')
        AND o.attempts < :maxAttempts
        AND o.createdAt >= :notBefore
      ORDER BY o.createdAt ASC
      """)
  List<FeeDueReminderOutboxEntity> findRetryable(
      @Param("maxAttempts") int maxAttempts,
      @Param("notBefore") Instant notBefore,
      Pageable pageable);
}
