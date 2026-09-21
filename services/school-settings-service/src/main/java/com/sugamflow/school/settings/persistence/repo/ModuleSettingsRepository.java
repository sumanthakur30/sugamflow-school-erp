package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.ModuleSettingsEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleSettingsRepository extends JpaRepository<ModuleSettingsEntity, Long> {
  Optional<ModuleSettingsEntity> findByOrganizationIdAndBranchIdAndModuleKey(
      String organizationId, String branchId, String moduleKey);
}
