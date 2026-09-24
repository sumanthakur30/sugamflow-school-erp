package com.sugamflow.school.support.ticket.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;

/**
 * Platform ops access for the shared Support queue (SugamFlow Superadmin).
 */
public final class SupportAdminAuthorization {
  public static final String MANAGE_SUPPORT_TICKETS = "MANAGE_SUPPORT_TICKETS";

  private SupportAdminAuthorization() {}

  public static void requireAdminPermission() {
    String role =
        TenantContext.get().map(TenantScope::roleCode).filter(r -> r != null && !r.isBlank()).orElse(null);
    if (role != null && "SUPER_ADMIN".equalsIgnoreCase(role.trim())) {
      return;
    }
    if (hasPermission(MANAGE_SUPPORT_TICKETS)) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Support admin access required");
  }

  public static String currentActor() {
    return TenantContext.get()
        .map(TenantScope::userId)
        .filter(u -> u != null && !u.isBlank())
        .orElse("system");
  }

  private static boolean hasPermission(String permission) {
    String header = requestHeader("X-Auth-Permissions");
    if (header == null || header.isBlank()) {
      return false;
    }
    for (String part : header.split("[,;\\s]+")) {
      if (permission.equalsIgnoreCase(part.trim())) {
        return true;
      }
    }
    return false;
  }

  private static String requestHeader(String name) {
    if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
      return null;
    }
    return attrs.getRequest().getHeader(name);
  }
}
