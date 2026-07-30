package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.PlanPriceScheduleEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPriceScheduleRepository extends JpaRepository<PlanPriceScheduleEntity, Long> {
  List<PlanPriceScheduleEntity> findByPlanIdOrderByEffectiveAtAsc(String planId);

  List<PlanPriceScheduleEntity> findByStatusIgnoreCaseAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
      String status, Instant effectiveAt);
}
