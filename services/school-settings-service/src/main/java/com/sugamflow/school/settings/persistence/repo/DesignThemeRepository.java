package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.DesignThemeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignThemeRepository extends JpaRepository<DesignThemeEntity, Long> {
  Optional<DesignThemeEntity> findByOrganizationIdAndBranchId(String organizationId, String branchId);
}
