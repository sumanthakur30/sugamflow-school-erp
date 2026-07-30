package com.sugamflow.school.subscription.cache;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default when Redis cache is disabled - always loads from supplier. */
@Component
@ConditionalOnProperty(
    prefix = "subscription.cache",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoOpSubscriptionCacheSupport implements SubscriptionCacheSupport {

  @Override
  public List<SubscriptionPlan> getPlans(Supplier<List<SubscriptionPlan>> loader) {
    return loader.get();
  }

  @Override
  public SubscriptionPlan getPlan(String planId, Supplier<SubscriptionPlan> loader) {
    return loader.get();
  }

  @Override
  public Map<String, Object> getEntitlements(
      String organizationId, Supplier<Map<String, Object>> loader) {
    return loader.get();
  }

  @Override
  public void evictPlan(String planId) {
    // no-op
  }

  @Override
  public void evictPlansList() {
    // no-op
  }

  @Override
  public void evictEntitlements(String organizationId) {
    // no-op
  }

  @Override
  public void evictAllEntitlements() {
    // no-op
  }

  @Override
  public void evictAll() {
    // no-op
  }
}