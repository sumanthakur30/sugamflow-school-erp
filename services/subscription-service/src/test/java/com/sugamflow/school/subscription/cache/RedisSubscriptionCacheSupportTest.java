package com.sugamflow.school.subscription.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sugamflow.school.subscription.model.SubscriptionPlan;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class RedisSubscriptionCacheSupportTest {

  private ConcurrentMapCacheManager cacheManager;
  private RedisSubscriptionCacheSupport cache;

  @BeforeEach
  void setUp() {
    cacheManager =
        new ConcurrentMapCacheManager(
            SubscriptionCacheSupport.CACHE_PLANS,
            SubscriptionCacheSupport.CACHE_PLAN,
            SubscriptionCacheSupport.CACHE_ENTITLEMENTS);
    cache = new RedisSubscriptionCacheSupport(cacheManager);
  }

  @Test
  void getEntitlementsCachesSecondCall() {
    AtomicInteger loads = new AtomicInteger();
    Map<String, Object> value = Map.of("planId", "starter");

    Map<String, Object> first =
        cache.getEntitlements(
            "ORG-1",
            () -> {
              loads.incrementAndGet();
              return value;
            });
    Map<String, Object> second =
        cache.getEntitlements(
            "ORG-1",
            () -> {
              loads.incrementAndGet();
              return Map.of("planId", "other");
            });

    assertEquals("starter", first.get("planId"));
    assertEquals("starter", second.get("planId"));
    assertEquals(1, loads.get());
  }

  @Test
  void evictEntitlementsForcesReload() {
    AtomicInteger loads = new AtomicInteger();
    cache.getEntitlements(
        "ORG-1",
        () -> {
          loads.incrementAndGet();
          return Map.of("n", loads.get());
        });
    cache.evictEntitlements("ORG-1");
    Map<String, Object> after =
        cache.getEntitlements(
            "ORG-1",
            () -> {
              loads.incrementAndGet();
              return Map.of("n", loads.get());
            });

    assertEquals(2, after.get("n"));
    assertEquals(2, loads.get());
  }

  @Test
  void getPlanCachesById() {
    SubscriptionPlan plan = SubscriptionPlan.starter();
    AtomicInteger loads = new AtomicInteger();
    SubscriptionPlan first = cache.getPlan("starter", () -> {
      loads.incrementAndGet();
      return plan;
    });
    SubscriptionPlan second = cache.getPlan("starter", () -> {
      loads.incrementAndGet();
      return null;
    });
    assertSame(first, second);
    assertEquals(1, loads.get());
  }

  @Test
  void getPlansCachesList() {
    AtomicInteger loads = new AtomicInteger();
    List<SubscriptionPlan> first =
        cache.getPlans(
            () -> {
              loads.incrementAndGet();
              return List.of(SubscriptionPlan.starter());
            });
    List<SubscriptionPlan> second =
        cache.getPlans(
            () -> {
              loads.incrementAndGet();
              return List.of();
            });
    assertEquals(1, first.size());
    assertEquals(1, second.size());
    assertEquals(1, loads.get());
  }

  @Test
  void evictAllClearsKnownCaches() {
    Cache entitlements = mock(Cache.class);
    Cache plans = mock(Cache.class);
    Cache plan = mock(Cache.class);
    CacheManagerStub stub = new CacheManagerStub(Map.of(
        SubscriptionCacheSupport.CACHE_ENTITLEMENTS, entitlements,
        SubscriptionCacheSupport.CACHE_PLANS, plans,
        SubscriptionCacheSupport.CACHE_PLAN, plan));
    RedisSubscriptionCacheSupport support = new RedisSubscriptionCacheSupport(stub);
    support.evictAll();
    verify(entitlements).clear();
    verify(plans).clear();
    verify(plan).clear();
  }

  @Test
  void noOpDoesNotCache() {
    NoOpSubscriptionCacheSupport noOp = new NoOpSubscriptionCacheSupport();
    AtomicInteger loads = new AtomicInteger();
    noOp.getEntitlements("ORG", () -> {
      loads.incrementAndGet();
      return Map.of("x", 1);
    });
    noOp.getEntitlements("ORG", () -> {
      loads.incrementAndGet();
      return Map.of("x", 2);
    });
    assertEquals(2, loads.get());
    noOp.evictAll(); // should not throw
  }

  private static final class CacheManagerStub implements org.springframework.cache.CacheManager {
    private final Map<String, Cache> caches;

    private CacheManagerStub(Map<String, Cache> caches) {
      this.caches = caches;
    }

    @Override
    public Cache getCache(String name) {
      return caches.get(name);
    }

    @Override
    public java.util.Collection<String> getCacheNames() {
      return caches.keySet();
    }
  }
}
