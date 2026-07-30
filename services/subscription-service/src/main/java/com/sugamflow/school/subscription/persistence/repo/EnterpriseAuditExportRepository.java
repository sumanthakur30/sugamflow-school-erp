package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.EnterpriseAuditExportEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnterpriseAuditExportRepository
    extends JpaRepository<EnterpriseAuditExportEntity, Long> {
  List<EnterpriseAuditExportEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
}
