package com.sugamflow.school.audit.web;

import com.sugamflow.school.audit.service.ConfigAuditService;
import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

  private final ConfigAuditService service;

  public AuditController(ConfigAuditService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @PostMapping("/config-changes")
  public ApiResponse<Map<String, Object>> record(@RequestBody Map<String, Object> body) {
    TenantScope t = TenantContext.require();
    return ApiResponse.ok(service.record(t.organizationId(), t.branchId(), t.userId(), body));
  }

  @GetMapping("/config-changes")
  public ApiResponse<List<Map<String, Object>>> list(
      @RequestParam(value = "entityType", required = false) String entityType,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "entityKey", required = false) String entityKey) {
    return ApiResponse.ok(
        service.list(
            TenantContext.require().organizationId(), entityType, status, entityKey));
  }

  @GetMapping("/config-changes/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("id") String id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping("/config-changes/{id}/approve")
  public ApiResponse<Map<String, Object>> approve(@PathVariable("id") String id) {
    return ApiResponse.ok(service.approve(id));
  }

  @PostMapping("/config-changes/{id}/rollback")
  public ApiResponse<Map<String, Object>> rollback(
      @PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> body) {
    String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", ""));
    if (reason != null && reason.isBlank()) {
      reason = null;
    }
    return ApiResponse.ok(service.rollback(id, TenantContext.require().userId(), reason));
  }
}
