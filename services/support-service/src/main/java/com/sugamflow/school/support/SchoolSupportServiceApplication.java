package com.sugamflow.school.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
@EntityScan(basePackages = "com.sugamflow.school.support.ticket.model")
@EnableJpaRepositories(basePackages = "com.sugamflow.school.support.ticket.repository")
public class SchoolSupportServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(SchoolSupportServiceApplication.class, args);
  }
}
