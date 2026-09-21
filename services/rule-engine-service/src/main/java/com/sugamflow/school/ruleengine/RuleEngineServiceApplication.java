package com.sugamflow.school.ruleengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class RuleEngineServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(RuleEngineServiceApplication.class, args);
  }
}
