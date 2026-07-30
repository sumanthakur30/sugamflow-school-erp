package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.PlanModuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanModuleRepository extends JpaRepository<PlanModuleEntity, PlanModuleEntity.Pk> {
  List<PlanModuleEntity> findByPlanIdOrderByModuleCodeAsc(String planId);

  @Modifying(clearAutomatically = true)
  @Query("delete from PlanModuleEntity p where p.planId = :planId")
  void deleteByPlanId(@Param("planId") String planId);
}
