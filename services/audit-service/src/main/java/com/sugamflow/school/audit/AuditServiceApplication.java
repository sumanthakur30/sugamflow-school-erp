package com.sugamflow.school.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class AuditServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(AuditServiceApplication.class, args);
  }
}
