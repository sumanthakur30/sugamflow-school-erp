package com.sugamflow.school.common.tenant;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

/** Shared outbound tenant headers for inter-service calls. */
public final class TenantHeaders {

  public static final String GATEWAY_VERIFIED = "X-Gateway-Verified";
  public static final String INTERNAL_SERVICE = "X-Internal-Service";

  private TenantHeaders() {}

  public static void apply(HttpHeaders headers, TenantScope scope) {
    if (scope == null || scope.organizationId() == null) {
      return;
    }
    headers.set(TenantFilter.TENANT_HEADER, scope.organizationId());
    if (scope.branchId() != null && !scope.branchId().isBlank()) {
      headers.set(TenantFilter.BRANCH_HEADER, scope.branchId());
    }
    if (scope.academicSessionId() != null && !scope.academicSessionId().isBlank()) {
      headers.set(TenantFilter.SESSION_HEADER, scope.academicSessionId());
    }
    if (scope.userId() != null && !scope.userId().isBlank()) {
      headers.set(TenantFilter.USER_HEADER, scope.userId());
      headers.set("X-Auth-User", scope.userId());
    }
    if (scope.roleCode() != null && !scope.roleCode().isBlank()) {
      headers.set(TenantFilter.ROLE_HEADER, scope.roleCode());
      headers.set("X-Auth-Role", scope.roleCode());
    }
    headers.set(GATEWAY_VERIFIED, "true");
    headers.set(INTERNAL_SERVICE, "true");
    // Propagate inbound correlation id on inter-service hops when present.
    String requestId = MDC.get(com.sugamflow.school.common.ops.CorrelationIdFilter.MDC_KEY);
    if (requestId != null && !requestId.isBlank()) {
      headers.set(com.sugamflow.school.common.ops.CorrelationIdFilter.HEADER, requestId);
    }
  }
}
