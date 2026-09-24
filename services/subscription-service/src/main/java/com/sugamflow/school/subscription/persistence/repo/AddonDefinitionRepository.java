package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.AddonDefinitionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddonDefinitionRepository extends JpaRepository<AddonDefinitionEntity, String> {
  List<AddonDefinitionEntity> findByActiveTrueOrderBySortOrderAsc();
}
