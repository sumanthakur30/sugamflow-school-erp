package com.sugamflow.school.exam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class ExamServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ExamServiceApplication.class, args);
  }
}
