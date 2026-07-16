package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.FinanceDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceDefinitionRepository extends JpaRepository<FinanceDefinitionEntity, String> {

  List<FinanceDefinitionEntity> findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
      String organizationId, String definitionType, String status);

  Optional<FinanceDefinitionEntity>
      findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
          String organizationId, String definitionType, String definitionKey, String status);

  boolean existsByOrganizationIdAndDefinitionType(String organizationId, String definitionType);
}
