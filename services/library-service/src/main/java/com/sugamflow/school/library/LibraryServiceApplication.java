package com.sugamflow.school.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.sugamflow.school")
@EnableDiscoveryClient
public class LibraryServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(LibraryServiceApplication.class, args);
  }
}
