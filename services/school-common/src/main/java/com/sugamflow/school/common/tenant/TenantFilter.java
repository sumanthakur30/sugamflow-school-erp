package com.sugamflow.school.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds {@link TenantContext} from gateway-injected headers. When {@code
 * school.security.require-gateway-verified=true} (default), rejects requests that did not pass the
 * API gateway (or an internal service hop that marks {@code X-Internal-Service}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantFilter extends OncePerRequestFilter {

  public static final String TENANT_HEADER = "X-Tenant-Id";
  public static final String BRANCH_HEADER = "X-Branch-Id";
  public static final String SESSION_HEADER = "X-Academic-Session-Id";
  public static final String USER_HEADER = "X-User-Id";
  public static final String ROLE_HEADER = "X-Role-Code";

  @Value("${school.security.require-gateway-verified:true}")
  private boolean requireGatewayVerified;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path != null && (path.startsWith("/actuator") || path.startsWith("/error"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    boolean verified =
        "true".equalsIgnoreCase(request.getHeader(TenantHeaders.GATEWAY_VERIFIED));
    boolean internal =
        "true".equalsIgnoreCase(request.getHeader(TenantHeaders.INTERNAL_SERVICE));
    if (requireGatewayVerified && !verified && !internal) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response
          .getWriter()
          .write(
              "{\"success\":false,\"message\":\"Gateway verification required. Call APIs via the API gateway.\"}");
      return;
    }

    try {
      // Prefer gateway-authored identity headers over client-supplied school headers.
      String user =
          firstNonBlank(request.getHeader("X-Auth-User"), request.getHeader(USER_HEADER));
      String role =
          firstNonBlank(request.getHeader("X-Auth-Role"), request.getHeader(ROLE_HEADER));
      TenantContext.set(
          new TenantScope(
              request.getHeader(TENANT_HEADER),
              request.getHeader(BRANCH_HEADER),
              request.getHeader(SESSION_HEADER),
              user,
              role));
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary.trim();
    }
    if (fallback != null && !fallback.isBlank()) {
      return fallback.trim();
    }
    return null;
  }
}
