package com.sugamflow.school.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class WorkflowServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(WorkflowServiceApplication.class, args);
  }
}
