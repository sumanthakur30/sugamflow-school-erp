package com.sugamflow.school.website.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WebsiteResolveServiceTest {

  @Test
  void normalizeHost_stripsSchemePortAndPath() {
    assertEquals("hcpschool.com", WebsiteResolveService.normalizeHost("https://HCPSchool.com:443/home"));
    assertEquals("hcp.localhost", WebsiteResolveService.normalizeHost("hcp.localhost:4200"));
    assertEquals("www.hcpschool.com", WebsiteResolveService.normalizeHost("WWW.hcpschool.com."));
  }
}
