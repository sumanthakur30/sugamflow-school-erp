package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.BusinessTypeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessTypeRepository extends JpaRepository<BusinessTypeEntity, String> {
  List<BusinessTypeEntity> findByActiveTrueOrderBySortOrderAscNameAsc();
}
