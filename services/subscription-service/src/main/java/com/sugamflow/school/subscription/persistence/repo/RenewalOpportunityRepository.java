package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.RenewalOpportunityEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RenewalOpportunityRepository
    extends JpaRepository<RenewalOpportunityEntity, Long> {
  Optional<RenewalOpportunityEntity> findByOrganizationId(String organizationId);

  List<RenewalOpportunityEntity> findAllByOrderByNextActionAtAscHealthScoreAsc();

  @Query(
      "select o.stage, count(o) from RenewalOpportunityEntity o group by o.stage order by count(o) desc")
  List<Object[]> countGroupedByStage();
}
