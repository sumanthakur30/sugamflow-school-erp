package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.SubscriptionInvoiceLineEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionInvoiceLineRepository
    extends JpaRepository<SubscriptionInvoiceLineEntity, Long> {
  List<SubscriptionInvoiceLineEntity> findByInvoiceIdOrderBySortOrderAscIdAsc(Long invoiceId);
}
