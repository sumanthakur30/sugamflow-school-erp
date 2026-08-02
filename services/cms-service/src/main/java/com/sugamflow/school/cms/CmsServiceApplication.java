package com.sugamflow.school.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class CmsServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(CmsServiceApplication.class, args);
  }
}
