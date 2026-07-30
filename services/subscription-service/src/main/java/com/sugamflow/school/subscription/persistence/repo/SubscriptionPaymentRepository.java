package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.SubscriptionPaymentEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionPaymentRepository
    extends JpaRepository<SubscriptionPaymentEntity, Long> {
  List<SubscriptionPaymentEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  Optional<SubscriptionPaymentEntity> findByProviderAndProviderPaymentId(
      String provider, String providerPaymentId);

  @Query(
      """
      select coalesce(sum(p.amountMinor), 0) from SubscriptionPaymentEntity p
      where upper(p.status) = 'SUCCESS' and p.createdAt >= :from
      """)
  long sumSuccessfulAmountSince(@Param("from") Instant from);

  @Query(
      """
      select p from SubscriptionPaymentEntity p
      where upper(p.status) = 'SUCCESS' and p.createdAt >= :from
      order by p.createdAt asc
      """)
  List<SubscriptionPaymentEntity> findSuccessfulSince(@Param("from") Instant from);

  long countByStatusIgnoreCase(String status);
}
