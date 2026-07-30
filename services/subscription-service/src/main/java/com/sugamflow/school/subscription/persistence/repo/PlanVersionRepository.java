package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.PlanVersionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanVersionRepository extends JpaRepository<PlanVersionEntity, Long> {
  List<PlanVersionEntity> findByPlanIdOrderByVersionNumberDesc(String planId);

  Optional<PlanVersionEntity> findFirstByPlanIdAndStatusIgnoreCaseOrderByVersionNumberDesc(
      String planId, String status);

  Optional<PlanVersionEntity> findByIdAndPlanId(Long id, String planId);

  @Query("select coalesce(max(v.versionNumber), 0) from PlanVersionEntity v where v.planId = :planId")
  int maxVersionNumber(@Param("planId") String planId);
}
