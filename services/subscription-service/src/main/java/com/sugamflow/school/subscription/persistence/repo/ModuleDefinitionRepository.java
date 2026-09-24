package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.ModuleDefinitionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleDefinitionRepository extends JpaRepository<ModuleDefinitionEntity, String> {
  List<ModuleDefinitionEntity> findByBusinessTypeCodeAndActiveTrueOrderBySortOrderAscNameAsc(
      String businessTypeCode);

  List<ModuleDefinitionEntity> findByActiveTrueOrderBySortOrderAscNameAsc();
}
