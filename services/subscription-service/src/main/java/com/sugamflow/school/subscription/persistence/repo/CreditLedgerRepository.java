package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.CreditLedgerEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntity, Long> {
  List<CreditLedgerEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  List<CreditLedgerEntity> findByOrganizationIdAndMeterCodeOrderByCreatedAtDesc(
      String organizationId, String meterCode);
}
