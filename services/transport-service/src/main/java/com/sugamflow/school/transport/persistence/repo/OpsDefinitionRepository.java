package com.sugamflow.school.transport.persistence.repo;

import com.sugamflow.school.transport.persistence.entity.OpsDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpsDefinitionRepository extends JpaRepository<OpsDefinitionEntity, String> {

  List<OpsDefinitionEntity> findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
      String organizationId, String definitionType, String status);

  Optional<OpsDefinitionEntity>
      findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
          String organizationId, String definitionType, String definitionKey, String status);

  boolean existsByOrganizationIdAndDefinitionType(String organizationId, String definitionType);
}
