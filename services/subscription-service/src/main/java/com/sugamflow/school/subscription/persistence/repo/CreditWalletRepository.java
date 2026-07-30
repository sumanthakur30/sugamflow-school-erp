package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.CreditWalletEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditWalletRepository
    extends JpaRepository<CreditWalletEntity, CreditWalletEntity.Pk> {
  List<CreditWalletEntity> findByOrganizationIdOrderByMeterCodeAsc(String organizationId);
}