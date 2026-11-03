package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.SchoolBillingContractEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolBillingContractRepository extends JpaRepository<SchoolBillingContractEntity, String> {
  List<SchoolBillingContractEntity> findByActiveTrueAndUnitModel(String unitModel);

  long countByActiveTrueAndUnitModel(String unitModel);
}
