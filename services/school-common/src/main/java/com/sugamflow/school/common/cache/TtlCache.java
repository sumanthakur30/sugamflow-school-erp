package com.sugamflow.school.common.cache;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Minimal thread-safe TTL cache for low-cardinality, read-heavy lookups
 * (feature flags, module settings, entitlements). Values are cached for a
 * fixed TTL; a null from the loader is never cached, so transient upstream
 * failures do not poison the cache.
 *
 * <p>TTL for config lookups defaults to 30s and can be tuned (or disabled
 * with 0) via the SCHOOL_CONFIG_CACHE_TTL_SECONDS environment variable.
 */
public final class TtlCache<K, V> {

  private record Entry<V>(V value, long expiresAtNanos) {}

  private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
  private final long ttlNanos;
  private final int maxEntries;

  public TtlCache(Duration ttl, int maxEntries) {
    this.ttlNanos = ttl.toNanos();
    this.maxEntries = maxEntries;
  }

  /** Cache sized/tuned for tenant config lookups (flags, settings). */
  public static <K, V> TtlCache<K, V> forConfig() {
    long ttlSeconds = 30;
    String raw = System.getenv("SCHOOL_CONFIG_CACHE_TTL_SECONDS");
    if (raw != null && !raw.isBlank()) {
      try {
        ttlSeconds = Long.parseLong(raw.trim());
      } catch (NumberFormatException ignored) {
        // keep default
      }
    }
    return new TtlCache<>(Duration.ofSeconds(ttlSeconds), 10_000);
  }

  /**
   * Returns the cached value for {@code key} or loads it. Loader results that
   * are null are returned but not cached.
   */
  public V get(K key, Function<K, V> loader) {
    if (ttlNanos <= 0) {
      return loader.apply(key);
    }
    long now = System.nanoTime();
    Entry<V> entry = map.get(key);
    if (entry != null && now < entry.expiresAtNanos) {
      return entry.value;
    }
    V value = loader.apply(key);
    if (value != null) {
      if (map.size() >= maxEntries) {
        map.clear();
      }
      map.put(key, new Entry<>(value, now + ttlNanos));
    } else if (entry != null) {
      map.remove(key, entry);
    }
    return value;
  }

  public void invalidate(K key) {
    map.remove(key);
  }

  public void clear() {
    map.clear();
  }
}
