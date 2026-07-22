package com.sugamflow.school.academic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class AcademicStructureServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(AcademicStructureServiceApplication.class, args);
  }
}
