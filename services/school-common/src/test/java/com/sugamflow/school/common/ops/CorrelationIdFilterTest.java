package com.sugamflow.school.common.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void generatesRequestIdWhenMissing() throws Exception {
    CorrelationIdFilter filter = new CorrelationIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    String id = response.getHeader(CorrelationIdFilter.HEADER);
    assertNotNull(id);
    assertFalse(id.isBlank());
    verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    assertEquals(null, MDC.get(CorrelationIdFilter.MDC_KEY)); // cleared after request
  }

  @Test
  void reusesInboundRequestId() throws Exception {
    CorrelationIdFilter filter = new CorrelationIdFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
    request.addHeader(CorrelationIdFilter.HEADER, "abc123");
    request.addHeader("X-Tenant-Id", "demo-school");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> {
          assertEquals("abc123", MDC.get(CorrelationIdFilter.MDC_KEY));
          assertEquals("demo-school", MDC.get(CorrelationIdFilter.TENANT_MDC));
        };

    filter.doFilter(request, response, chain);

    assertEquals("abc123", response.getHeader(CorrelationIdFilter.HEADER));
  }

  @Test
  void accessLogSkipsActuator() {
    AccessLogFilter filter = new AccessLogFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    assertTrue(filter.shouldNotFilter(request));
  }
}
