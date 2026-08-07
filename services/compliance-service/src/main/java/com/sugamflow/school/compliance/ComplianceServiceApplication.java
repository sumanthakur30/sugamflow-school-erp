package com.sugamflow.school.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class ComplianceServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ComplianceServiceApplication.class, args);
  }
}
