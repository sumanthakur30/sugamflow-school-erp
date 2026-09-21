package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.BillingCouponEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingCouponRepository extends JpaRepository<BillingCouponEntity, String> {
  List<BillingCouponEntity> findAllByOrderByCodeAsc();

  Optional<BillingCouponEntity> findByCodeIgnoreCase(String code);
}
