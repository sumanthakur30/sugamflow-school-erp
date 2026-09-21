package com.sugamflow.school.website;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
@EnableScheduling
public class WebsiteServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(WebsiteServiceApplication.class, args);
  }
}
