package com.sugamflow.school.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class TenantFilterTest {

  private TenantFilter filter;

  @BeforeEach
  void setUp() {
    filter = new TenantFilter();
    ReflectionTestUtils.setField(filter, "requireGatewayVerified", true);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  @Test
  void rejectsDirectCallWithoutGatewayVerification() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/students");
    request.addHeader(TenantFilter.TENANT_HEADER, "NAT-01");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    assertNull(chain.getRequest(), "request must not reach downstream handlers");
  }

  @Test
  void acceptsGatewayVerifiedRequestAndBindsTenant() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/students");
    request.addHeader(TenantHeaders.GATEWAY_VERIFIED, "true");
    request.addHeader(TenantFilter.TENANT_HEADER, "NAT-01");
    request.addHeader(TenantFilter.BRANCH_HEADER, "main");
    request.addHeader(TenantFilter.SESSION_HEADER, "2025-26");
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<TenantScope> seen = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain(
            new jakarta.servlet.http.HttpServlet() {
              @Override
              protected void service(
                  jakarta.servlet.http.HttpServletRequest req,
                  jakarta.servlet.http.HttpServletResponse res) {
                seen.set(TenantContext.get().orElse(null));
              }
            });

    filter.doFilter(request, response, chain);

    assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    assertNotNull(seen.get());
    assertEquals("NAT-01", seen.get().organizationId());
    assertEquals("main", seen.get().branchId());
    assertEquals("2025-26", seen.get().academicSessionId());
  }

  @Test
  void contextClearedAfterRequestCompletes() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/config/theme");
    request.addHeader(TenantHeaders.GATEWAY_VERIFIED, "true");
    request.addHeader(TenantFilter.TENANT_HEADER, "NAT-01");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertTrue(TenantContext.get().isEmpty(), "tenant scope must not leak across requests");
  }

  @Test
  void gatewayIdentityHeadersWinOverClientSuppliedOnes() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/student/students");
    request.addHeader(TenantHeaders.GATEWAY_VERIFIED, "true");
    request.addHeader(TenantFilter.TENANT_HEADER, "NAT-01");
    // Client tries to spoof elevated identity; gateway-authored headers must win.
    request.addHeader(TenantFilter.USER_HEADER, "attacker");
    request.addHeader(TenantFilter.ROLE_HEADER, "SUPER_ADMIN");
    request.addHeader("X-Auth-User", "real-user");
    request.addHeader("X-Auth-Role", "TEACHER");
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<TenantScope> seen = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain(
            new jakarta.servlet.http.HttpServlet() {
              @Override
              protected void service(
                  jakarta.servlet.http.HttpServletRequest req,
                  jakarta.servlet.http.HttpServletResponse res) {
                seen.set(TenantContext.get().orElse(null));
              }
            });

    filter.doFilter(request, response, chain);

    assertEquals("real-user", seen.get().userId());
    assertEquals("TEACHER", seen.get().roleCode());
  }

  @Test
  void actuatorPathsSkipGatewayCheck() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    request.setRequestURI("/actuator/health");
    assertTrue(filter.shouldNotFilter(request));
  }
}
