package com.sugamflow.school.settings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class SchoolSettingsServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(SchoolSettingsServiceApplication.class, args);
  }
}
