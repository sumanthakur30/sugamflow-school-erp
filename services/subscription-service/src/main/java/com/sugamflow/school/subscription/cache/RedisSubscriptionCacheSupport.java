package com.sugamflow.school.subscription.cache;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Redis-backed cache for plans and entitlements. */
public class RedisSubscriptionCacheSupport implements SubscriptionCacheSupport {

  private static final Logger log = LoggerFactory.getLogger(RedisSubscriptionCacheSupport.class);
  private static final String PLANS_KEY = "all";

  private final CacheManager cacheManager;

  public RedisSubscriptionCacheSupport(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<SubscriptionPlan> getPlans(Supplier<List<SubscriptionPlan>> loader) {
    Cache cache = cache(CACHE_PLANS);
    if (cache == null) {
      return loader.get();
    }
    List<SubscriptionPlan> cached = cache.get(PLANS_KEY, List.class);
    if (cached != null) {
      return cached;
    }
    List<SubscriptionPlan> loaded = loader.get();
    if (loaded != null) {
      cache.put(PLANS_KEY, loaded);
    }
    return loaded;
  }

  @Override
  public SubscriptionPlan getPlan(String planId, Supplier<SubscriptionPlan> loader) {
    if (planId == null) {
      return loader.get();
    }
    Cache cache = cache(CACHE_PLAN);
    if (cache == null) {
      return loader.get();
    }
    SubscriptionPlan cached = cache.get(planId, SubscriptionPlan.class);
    if (cached != null) {
      return cached;
    }
    SubscriptionPlan loaded = loader.get();
    if (loaded != null) {
      cache.put(planId, loaded);
    }
    return loaded;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> getEntitlements(
      String organizationId, Supplier<Map<String, Object>> loader) {
    if (organizationId == null) {
      return loader.get();
    }
    Cache cache = cache(CACHE_ENTITLEMENTS);
    if (cache == null) {
      return loader.get();
    }
    Map<String, Object> cached = cache.get(organizationId, Map.class);
    if (cached != null) {
      return cached;
    }
    Map<String, Object> loaded = loader.get();
    if (loaded != null) {
      cache.put(organizationId, loaded);
    }
    return loaded;
  }

  @Override
  public void evictPlan(String planId) {
    Cache cache = cache(CACHE_PLAN);
    if (cache != null && planId != null) {
      cache.evict(planId);
    }
    evictPlansList();
  }

  @Override
  public void evictPlansList() {
    Cache cache = cache(CACHE_PLANS);
    if (cache != null) {
      cache.evict(PLANS_KEY);
    }
  }

  @Override
  public void evictEntitlements(String organizationId) {
    Cache cache = cache(CACHE_ENTITLEMENTS);
    if (cache != null && organizationId != null) {
      cache.evict(organizationId);
    }
  }

  @Override
  public void evictAllEntitlements() {
    Cache cache = cache(CACHE_ENTITLEMENTS);
    if (cache != null) {
      cache.clear();
    }
  }

  @Override
  public void evictAll() {
    clearQuietly(CACHE_PLANS);
    clearQuietly(CACHE_PLAN);
    clearQuietly(CACHE_ENTITLEMENTS);
    log.debug("Cleared all subscription Redis caches");
  }

  private Cache cache(String name) {
    return cacheManager.getCache(name);
  }

  private void clearQuietly(String name) {
    Cache cache = cache(name);
    if (cache != null) {
      cache.clear();
    }
  }
}
