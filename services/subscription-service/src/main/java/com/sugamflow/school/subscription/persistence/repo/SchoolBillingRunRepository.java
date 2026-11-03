package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.SchoolBillingRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolBillingRunRepository extends JpaRepository<SchoolBillingRunEntity, Long> {
  Optional<SchoolBillingRunEntity> findByOrganizationIdAndPeriodKeyAndObligationType(
      String organizationId, String periodKey, String obligationType);

  List<SchoolBillingRunEntity> findByPeriodKeyAndObligationTypeOrderByOrganizationIdAsc(
      String periodKey, String obligationType);

  List<SchoolBillingRunEntity> findByOrganizationIdAndObligationTypeOrderByPeriodKeyDesc(
      String organizationId, String obligationType);
}
