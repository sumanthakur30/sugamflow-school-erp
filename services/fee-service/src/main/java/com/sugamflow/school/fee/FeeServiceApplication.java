package com.sugamflow.school.fee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
@EnableScheduling
public class FeeServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(FeeServiceApplication.class, args);
  }
}
