package com.sugamflow.school.staff.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(StaffProperties.class)
public class StaffClientConfig {

  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
