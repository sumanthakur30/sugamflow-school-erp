package com.sugamflow.school.reportbuilder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class ReportBuilderServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ReportBuilderServiceApplication.class, args);
  }
}
