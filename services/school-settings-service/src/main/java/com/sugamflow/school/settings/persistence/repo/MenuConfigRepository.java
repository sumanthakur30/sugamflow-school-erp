package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.MenuConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuConfigRepository extends JpaRepository<MenuConfigEntity, Long> {
  Optional<MenuConfigEntity> findByOrganizationId(String organizationId);
}
