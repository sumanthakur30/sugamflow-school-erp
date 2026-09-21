package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.AiSettingsEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSettingsRepository extends JpaRepository<AiSettingsEntity, Long> {
  Optional<AiSettingsEntity> findByOrganizationId(String organizationId);
}
