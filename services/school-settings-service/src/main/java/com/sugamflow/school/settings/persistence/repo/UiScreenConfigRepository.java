package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.UiScreenConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UiScreenConfigRepository extends JpaRepository<UiScreenConfigEntity, Long> {
  Optional<UiScreenConfigEntity> findByOrganizationIdAndScreenKey(
      String organizationId, String screenKey);
}
