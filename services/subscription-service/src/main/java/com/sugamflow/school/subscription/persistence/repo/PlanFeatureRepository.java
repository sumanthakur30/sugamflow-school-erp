package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.PlanFeatureEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanFeatureRepository extends JpaRepository<PlanFeatureEntity, PlanFeatureEntity.Pk> {
  List<PlanFeatureEntity> findByPlanIdOrderByFeatureCodeAsc(String planId);

  @Modifying(clearAutomatically = true)
  @Query("delete from PlanFeatureEntity p where p.planId = :planId")
  void deleteByPlanId(@Param("planId") String planId);
}
