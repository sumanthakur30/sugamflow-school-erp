package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.RenewalReminderEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RenewalReminderRepository extends JpaRepository<RenewalReminderEntity, Long> {
  List<RenewalReminderEntity> findByStatusIgnoreCaseOrderByDueAtAsc(String status);

  List<RenewalReminderEntity> findByOrganizationIdOrderByDueAtDesc(String organizationId);

  Optional<RenewalReminderEntity> findByOrganizationIdAndReminderTypeAndDueAtAndStatusIgnoreCase(
      String organizationId, String reminderType, Instant dueAt, String status);

  long countByStatusIgnoreCase(String status);
}
