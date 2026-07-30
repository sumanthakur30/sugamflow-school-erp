package com.sugamflow.school.subscription.cache;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Plan / entitlement snapshot cache (Redis when enabled; otherwise no-op). */
public interface SubscriptionCacheSupport {

  String CACHE_PLANS = "subscriptionPlans";
  String CACHE_PLAN = "subscriptionPlan";
  String CACHE_ENTITLEMENTS = "subscriptionEntitlements";

  List<SubscriptionPlan> getPlans(Supplier<List<SubscriptionPlan>> loader);

  SubscriptionPlan getPlan(String planId, Supplier<SubscriptionPlan> loader);

  Map<String, Object> getEntitlements(String organizationId, Supplier<Map<String, Object>> loader);

  void evictPlan(String planId);

  void evictPlansList();

  void evictEntitlements(String organizationId);

  void evictAllEntitlements();

  /** Clear all subscription caches (plan edits / seed backfill). */
  void evictAll();
}
