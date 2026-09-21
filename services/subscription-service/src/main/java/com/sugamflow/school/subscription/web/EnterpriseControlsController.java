package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.subscription.service.EnterpriseControlsService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 14 enterprise controls: SSO / white-label / residency / HA config + audit export.
 * Configuration store only — IdP runtime and Design Studio branding remain elsewhere.
 */
@RestController
@RequestMapping("/api/subscription")
public class EnterpriseControlsController {

  private final EnterpriseControlsService enterpriseControlsService;

  public EnterpriseControlsController(EnterpriseControlsService enterpriseControlsService) {
    this.enterpriseControlsService = enterpriseControlsService;
  }

  @GetMapping({"/enterprise/dashboard", "/subscriptions/enterprise/dashboard"})
  public ApiResponse<Map<String, Object>> dashboard() {
    return ApiResponse.ok(enterpriseControlsService.dashboard());
  }

  @GetMapping({
    "/enterprise/tenants/{organizationId}/settings",
    "/subscriptions/enterprise/tenants/{organizationId}/settings"
  })
  public ApiResponse<Map<String, Object>> getSettings(@PathVariable String organizationId) {
    return ApiResponse.ok(enterpriseControlsService.getSettings(organizationId));
  }

  @PutMapping({
    "/enterprise/tenants/{organizationId}/settings",
    "/subscriptions/enterprise/tenants/{organizationId}/settings"
  })
  public ApiResponse<Map<String, Object>> upsertSettings(
      @PathVariable String organizationId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(enterpriseControlsService.upsertSettings(organizationId, body));
  }

  @GetMapping({
    "/enterprise/tenants/{organizationId}/audit-events",
    "/subscriptions/enterprise/tenants/{organizationId}/audit-events"
  })
  public ApiResponse<List<Map<String, Object>>> auditEvents(
      @PathVariable String organizationId, @RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.ok(enterpriseControlsService.listAuditEvents(organizationId, limit));
  }

  @PostMapping({
    "/enterprise/tenants/{organizationId}/audit-exports",
    "/subscriptions/enterprise/tenants/{organizationId}/audit-exports"
  })
  public ApiResponse<Map<String, Object>> createExport(
      @PathVariable String organizationId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(enterpriseControlsService.createAuditExport(organizationId, body));
  }

  @GetMapping({
    "/enterprise/tenants/{organizationId}/audit-exports",
    "/subscriptions/enterprise/tenants/{organizationId}/audit-exports"
  })
  public ApiResponse<List<Map<String, Object>>> listExports(@PathVariable String organizationId) {
    return ApiResponse.ok(enterpriseControlsService.listExports(organizationId));
  }

  @GetMapping({
    "/enterprise/tenants/{organizationId}/audit-exports/{exportId}",
    "/subscriptions/enterprise/tenants/{organizationId}/audit-exports/{exportId}"
  })
  public ApiResponse<Map<String, Object>> getExport(
      @PathVariable String organizationId, @PathVariable long exportId) {
    return ApiResponse.ok(enterpriseControlsService.getExport(organizationId, exportId));
  }
}
