package com.sugamflow.school.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class AttendanceServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(AttendanceServiceApplication.class, args);
  }
}
