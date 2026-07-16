package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionRepository
    extends JpaRepository<TenantSubscriptionEntity, String> {}
