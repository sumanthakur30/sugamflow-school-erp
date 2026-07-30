package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.LimitDefinitionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimitDefinitionRepository extends JpaRepository<LimitDefinitionEntity, String> {
  List<LimitDefinitionEntity> findByBusinessTypeCodeAndActiveTrueOrderBySortOrderAscNameAsc(
      String businessTypeCode);

  List<LimitDefinitionEntity> findByActiveTrueOrderBySortOrderAscNameAsc();
}
