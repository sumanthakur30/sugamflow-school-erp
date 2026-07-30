package com.sugamflow.school.subscription.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.sugamflow.school.subscription.cache.RedisSubscriptionCacheSupport;
import com.sugamflow.school.subscription.cache.SubscriptionCacheSupport;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

@Configuration
@EnableCaching
@EnableConfigurationProperties(SubscriptionCacheProperties.class)
@ConditionalOnProperty(prefix = "subscription.cache", name = "enabled", havingValue = "true")
public class SubscriptionRedisCacheConfig {

  @Bean
  LettuceConnectionFactory subscriptionRedisConnectionFactory(
      SubscriptionCacheProperties properties) {
    RedisStandaloneConfiguration standalone =
        new RedisStandaloneConfiguration(properties.getHost(), properties.getPort());
    standalone.setDatabase(properties.getDatabase());
    if (StringUtils.hasText(properties.getPassword())) {
      standalone.setPassword(RedisPassword.of(properties.getPassword()));
    }
    return new LettuceConnectionFactory(standalone);
  }

  @Bean
  @Primary
  CacheManager subscriptionRedisCacheManager(
      LettuceConnectionFactory subscriptionRedisConnectionFactory,
      SubscriptionCacheProperties properties) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY);
    GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer(mapper);

    RedisCacheConfiguration defaults =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(Math.max(1, properties.getTtlSeconds())))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json))
            .prefixCacheNameWith("school:subscription:");

    return RedisCacheManager.builder(subscriptionRedisConnectionFactory)
        .cacheDefaults(defaults)
        .initialCacheNames(
            Set.of(
                SubscriptionCacheSupport.CACHE_PLANS,
                SubscriptionCacheSupport.CACHE_PLAN,
                SubscriptionCacheSupport.CACHE_ENTITLEMENTS))
        .build();
  }

  @Bean
  @Primary
  SubscriptionCacheSupport redisSubscriptionCacheSupport(CacheManager subscriptionRedisCacheManager) {
    return new RedisSubscriptionCacheSupport(subscriptionRedisCacheManager);
  }
}
