package com.sugamflow.school.subscription;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Redis autoconfig is excluded by default so local/dev without Redis still boots. Phase 7 enables
 * Redis explicitly when {@code subscription.cache.enabled=true}.
 */
@SpringBootApplication(
    scanBasePackages = "com.sugamflow.school",
    exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
@EnableDiscoveryClient
public class SubscriptionServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(SubscriptionServiceApplication.class, args);
  }
}
