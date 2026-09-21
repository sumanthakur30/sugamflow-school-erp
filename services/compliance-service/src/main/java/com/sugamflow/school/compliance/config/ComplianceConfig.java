package com.sugamflow.school.compliance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableConfigurationProperties(ComplianceProperties.class)
public class ComplianceConfig {
  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
