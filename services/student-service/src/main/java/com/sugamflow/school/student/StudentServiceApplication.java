package com.sugamflow.school.student;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class StudentServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(StudentServiceApplication.class, args);
  }
}
