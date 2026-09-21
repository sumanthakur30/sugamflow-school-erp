package com.sugamflow.school.hostel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class HostelServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(HostelServiceApplication.class, args);
  }
}
