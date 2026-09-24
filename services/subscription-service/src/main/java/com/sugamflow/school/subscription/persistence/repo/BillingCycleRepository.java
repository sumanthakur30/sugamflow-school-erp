package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.BillingCycleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingCycleRepository extends JpaRepository<BillingCycleEntity, String> {
  List<BillingCycleEntity> findByActiveTrueOrderBySortOrderAsc();
}
