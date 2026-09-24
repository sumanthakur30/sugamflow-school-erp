package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.FeatureDefinitionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureDefinitionRepository extends JpaRepository<FeatureDefinitionEntity, String> {
  List<FeatureDefinitionEntity> findByModuleCodeAndActiveTrueOrderBySortOrderAscNameAsc(
      String moduleCode);

  List<FeatureDefinitionEntity> findByActiveTrueOrderBySortOrderAscNameAsc();
}
