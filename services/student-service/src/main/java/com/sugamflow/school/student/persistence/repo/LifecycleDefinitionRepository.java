package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.LifecycleDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifecycleDefinitionRepository
    extends JpaRepository<LifecycleDefinitionEntity, String> {

  List<LifecycleDefinitionEntity>
      findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
          String organizationId, String definitionType, String status);

  Optional<LifecycleDefinitionEntity>
      findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
          String organizationId, String definitionType, String definitionKey, String status);

  boolean existsByOrganizationIdAndDefinitionType(String organizationId, String definitionType);
}
