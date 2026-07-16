package com.sugamflow.school.audit.persistence.repo;

import com.sugamflow.school.audit.persistence.entity.ConfigChangeAuditEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfigChangeAuditRepository extends JpaRepository<ConfigChangeAuditEntity, String> {

  List<ConfigChangeAuditEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  @Query(
      """
      SELECT e FROM ConfigChangeAuditEntity e
      WHERE e.organizationId = :org
        AND (:entityType IS NULL OR :entityType = '' OR e.entityType = :entityType)
        AND (:status IS NULL OR :status = '' OR e.status = :status)
        AND (:entityKey IS NULL OR :entityKey = '' OR e.entityKey = :entityKey)
      ORDER BY e.createdAt DESC
      """)
  List<ConfigChangeAuditEntity> search(
      @Param("org") String org,
      @Param("entityType") String entityType,
      @Param("status") String status,
      @Param("entityKey") String entityKey);
}
