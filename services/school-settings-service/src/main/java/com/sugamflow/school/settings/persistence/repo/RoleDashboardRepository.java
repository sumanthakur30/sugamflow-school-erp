package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.RoleDashboardEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleDashboardRepository extends JpaRepository<RoleDashboardEntity, Long> {
  Optional<RoleDashboardEntity> findByOrganizationIdAndRoleCode(
      String organizationId, String roleCode);
}
