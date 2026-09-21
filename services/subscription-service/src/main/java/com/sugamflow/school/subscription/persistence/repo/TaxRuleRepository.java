package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.TaxRuleEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRuleRepository extends JpaRepository<TaxRuleEntity, String> {
  List<TaxRuleEntity> findAllByOrderByCodeAsc();

  Optional<TaxRuleEntity> findByCodeIgnoreCase(String code);

  Optional<TaxRuleEntity> findFirstByDefaultRuleTrueAndActiveTrue();
}
