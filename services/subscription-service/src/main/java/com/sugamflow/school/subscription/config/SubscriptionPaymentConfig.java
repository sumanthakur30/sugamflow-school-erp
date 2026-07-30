package com.sugamflow.school.subscription.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SubscriptionPaymentProperties.class)
public class SubscriptionPaymentConfig {

  @Bean
  RestClient.Builder subscriptionRestClientBuilder() {
    return RestClient.builder();
  }
}
