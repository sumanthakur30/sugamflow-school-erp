package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.CreditPeriodRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditPeriodRunRepository extends JpaRepository<CreditPeriodRunEntity, Long> {
  Optional<CreditPeriodRunEntity> findByOrganizationIdAndMeterCodeAndPeriodKey(
      String organizationId, String meterCode, String periodKey);

  List<CreditPeriodRunEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);
}
