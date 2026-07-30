package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.TenantAddonEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAddonRepository extends JpaRepository<TenantAddonEntity, Long> {
  List<TenantAddonEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  List<TenantAddonEntity> findByInvoiceId(Long invoiceId);
}
