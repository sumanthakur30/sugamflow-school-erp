package com.sugamflow.school.common.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Structured access log for every non-actuator request: method, path, status, duration, and
 * correlation fields already present in MDC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class AccessLogFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path != null && (path.startsWith("/actuator") || path.startsWith("/error"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.currentTimeMillis();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long took = System.currentTimeMillis() - start;
      log.info(
          "access method={} path={} status={} durationMs={} requestId={} tenantId={} userId={}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          took,
          MDC.get(CorrelationIdFilter.MDC_KEY),
          MDC.get(CorrelationIdFilter.TENANT_MDC),
          MDC.get(CorrelationIdFilter.USER_MDC));
    }
  }
}
