package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.CreditPolicyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditPolicyRepository extends JpaRepository<CreditPolicyEntity, String> {
  List<CreditPolicyEntity> findByActiveTrueOrderByMeterCodeAsc();
}
