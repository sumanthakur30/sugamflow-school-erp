package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.SensitiveExportAuditEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensitiveExportAuditRepository extends JpaRepository<SensitiveExportAuditEntity, UUID> {

  List<SensitiveExportAuditEntity> findTop30ByOrganizationIdOrderByCreatedAtDesc(String organizationId);
}
