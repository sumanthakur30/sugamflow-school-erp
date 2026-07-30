package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.PlanLimitEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanLimitRepository extends JpaRepository<PlanLimitEntity, PlanLimitEntity.Pk> {
  List<PlanLimitEntity> findByPlanIdOrderByLimitCodeAsc(String planId);

  @Modifying(clearAutomatically = true)
  @Query("delete from PlanLimitEntity p where p.planId = :planId")
  void deleteByPlanId(@Param("planId") String planId);
}
