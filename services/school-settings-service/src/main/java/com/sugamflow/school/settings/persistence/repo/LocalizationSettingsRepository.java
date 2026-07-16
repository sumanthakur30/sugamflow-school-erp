package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.LocalizationSettingsEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalizationSettingsRepository
    extends JpaRepository<LocalizationSettingsEntity, Long> {
  Optional<LocalizationSettingsEntity> findByOrganizationIdAndBranchId(
      String organizationId, String branchId);
}
