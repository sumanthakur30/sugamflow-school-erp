package com.sugamflow.school.common.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propagates {@code X-Request-Id} for end-to-end tracing across gateway → school services →
 * inter-service hops. Puts the id into MDC so every log line for the request can be correlated.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  public static final String MDC_KEY = "requestId";
  public static final String TENANT_MDC = "tenantId";
  public static final String USER_MDC = "userId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = request.getHeader(HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString().replace("-", "");
    }
    MDC.put(MDC_KEY, requestId);
    String tenant = request.getHeader("X-Tenant-Id");
    if (tenant != null && !tenant.isBlank()) {
      MDC.put(TENANT_MDC, tenant.trim());
    }
    String user = request.getHeader("X-Auth-User");
    if (user == null || user.isBlank()) {
      user = request.getHeader("X-User-Id");
    }
    if (user != null && !user.isBlank()) {
      MDC.put(USER_MDC, user.trim());
    }
    response.setHeader(HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
      MDC.remove(TENANT_MDC);
      MDC.remove(USER_MDC);
    }
  }
}
