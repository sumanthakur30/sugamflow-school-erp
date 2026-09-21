package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.UsageCounterEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsageCounterRepository
    extends JpaRepository<UsageCounterEntity, UsageCounterEntity.Pk> {

  List<UsageCounterEntity> findByOrganizationIdOrderByLimitCodeAscPeriodKeyAsc(String organizationId);

  Optional<UsageCounterEntity> findByOrganizationIdAndLimitCodeAndPeriodKey(
      String organizationId, String limitCode, String periodKey);

  @Query(
      """
      select u from UsageCounterEntity u
      where u.usedValue > 0
      order by u.usedValue desc
      """)
  List<UsageCounterEntity> findTopUsed();
}
