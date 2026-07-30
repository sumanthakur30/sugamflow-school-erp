package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.EnterpriseAuditEventEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnterpriseAuditEventRepository
    extends JpaRepository<EnterpriseAuditEventEntity, Long> {

  List<EnterpriseAuditEventEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  @Query(
      """
      select e from EnterpriseAuditEventEntity e
      where e.organizationId = :org
        and (:fromAt is null or e.createdAt >= :fromAt)
        and (:toAt is null or e.createdAt <= :toAt)
      order by e.createdAt asc
      """)
  List<EnterpriseAuditEventEntity> findForExport(
      @Param("org") String organizationId,
      @Param("fromAt") Instant fromAt,
      @Param("toAt") Instant toAt);
}
