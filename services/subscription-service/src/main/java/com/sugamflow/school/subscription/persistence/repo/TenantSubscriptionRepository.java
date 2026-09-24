package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenantSubscriptionRepository
    extends JpaRepository<TenantSubscriptionEntity, String> {

  @Query(
      "select t.planId, count(t) from TenantSubscriptionEntity t group by t.planId order by count(t) desc")
  List<Object[]> countGroupedByPlanId();
}
