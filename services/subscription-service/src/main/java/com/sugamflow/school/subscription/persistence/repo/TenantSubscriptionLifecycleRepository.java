package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionLifecycleEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantSubscriptionLifecycleRepository
    extends JpaRepository<TenantSubscriptionLifecycleEntity, String> {

  long countByStatusIgnoreCase(String status);

  @Query(
      "select l.status, count(l) from TenantSubscriptionLifecycleEntity l group by l.status order by count(l) desc")
  List<Object[]> countGroupedByStatus();

  @Query(
      """
      select l from TenantSubscriptionLifecycleEntity l
      where l.expiresAt is not null
        and l.expiresAt >= :from
        and l.expiresAt < :to
      order by l.expiresAt asc
      """)
  List<TenantSubscriptionLifecycleEntity> findExpiringBetween(
      @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      """
      select count(l) from TenantSubscriptionLifecycleEntity l
      where l.expiresAt is not null
        and l.expiresAt >= :from
        and l.expiresAt < :to
      """)
  long countExpiringBetween(@Param("from") Instant from, @Param("to") Instant to);
}
