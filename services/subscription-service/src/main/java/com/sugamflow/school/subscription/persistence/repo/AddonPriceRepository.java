package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.AddonPriceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddonPriceRepository extends JpaRepository<AddonPriceEntity, Long> {
  List<AddonPriceEntity> findByPriceBookIdAndActiveTrueOrderBySkuAsc(String priceBookId);

  Optional<AddonPriceEntity> findBySkuAndPriceBookIdAndBillingCycleCodeAndActiveTrue(
      String sku, String priceBookId, String billingCycleCode);
}
