package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.PlanPriceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPriceRepository extends JpaRepository<PlanPriceEntity, Long> {
  List<PlanPriceEntity> findByPriceBookIdOrderByPlanIdAscBillingCycleCodeAsc(String priceBookId);

  List<PlanPriceEntity> findByPlanIdAndActiveTrueOrderByBillingCycleCodeAsc(String planId);

  Optional<PlanPriceEntity> findByPriceBookIdAndPlanIdAndBillingCycleCode(
      String priceBookId, String planId, String billingCycleCode);
}
