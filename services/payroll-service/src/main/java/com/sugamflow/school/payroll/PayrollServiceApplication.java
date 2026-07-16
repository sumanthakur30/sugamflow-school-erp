package com.sugamflow.school.payroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class PayrollServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(PayrollServiceApplication.class, args);
  }
}
