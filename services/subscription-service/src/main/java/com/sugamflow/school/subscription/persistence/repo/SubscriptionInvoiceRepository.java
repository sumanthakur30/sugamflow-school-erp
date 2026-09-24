package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.SubscriptionInvoiceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubscriptionInvoiceRepository
    extends JpaRepository<SubscriptionInvoiceEntity, Long> {
  List<SubscriptionInvoiceEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  Optional<SubscriptionInvoiceEntity> findByIdAndOrganizationId(Long id, String organizationId);

  Optional<SubscriptionInvoiceEntity> findByGatewayOrderId(String gatewayOrderId);

  long countByStatusIgnoreCase(String status);

  @Query(
      """
      select coalesce(sum(i.totalMinor), 0) from SubscriptionInvoiceEntity i
      where upper(i.status) = 'ISSUED'
      """)
  long sumIssuedOutstandingMinor();

  @Query(
      "select i.status, count(i) from SubscriptionInvoiceEntity i group by i.status order by count(i) desc")
  List<Object[]> countGroupedByStatus();
}
