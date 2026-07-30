package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.subscription.service.SubscriptionLifecycleService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Additive lifecycle / license APIs. Does not change entitlements or feature-flag contracts.
 */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionLifecycleController {

  private final SubscriptionLifecycleService lifecycleService;

  public SubscriptionLifecycleController(SubscriptionLifecycleService lifecycleService) {
    this.lifecycleService = lifecycleService;
  }

  @GetMapping({"/tenants/current/license", "/subscriptions/license"})
  public ApiResponse<Map<String, Object>> license() {
    String org = TenantContext.require().organizationId();
    return ApiResponse.ok(lifecycleService.getLicense(org));
  }

  @PutMapping("/tenants/current/license")
  public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
    String org = TenantContext.require().organizationId();
    return ApiResponse.ok(lifecycleService.updateLicense(org, body));
  }

  @PostMapping("/tenants/current/license/renew")
  public ApiResponse<Map<String, Object>> renew(@RequestBody(required = false) Map<String, Object> body) {
    String org = TenantContext.require().organizationId();
    return ApiResponse.ok(lifecycleService.renew(org, body == null ? Map.of() : body));
  }

  @PostMapping("/tenants/current/license/suspend")
  public ApiResponse<Map<String, Object>> suspend(@RequestBody(required = false) Map<String, Object> body) {
    String org = TenantContext.require().organizationId();
    String notes = body == null || body.get("notes") == null ? null : String.valueOf(body.get("notes"));
    return ApiResponse.ok(lifecycleService.suspend(org, notes));
  }

  @PostMapping("/tenants/current/license/cancel")
  public ApiResponse<Map<String, Object>> cancel(@RequestBody(required = false) Map<String, Object> body) {
    String org = TenantContext.require().organizationId();
    String notes = body == null || body.get("notes") == null ? null : String.valueOf(body.get("notes"));
    return ApiResponse.ok(lifecycleService.cancel(org, notes));
  }

  @PostMapping("/tenants/current/license/resume")
  public ApiResponse<Map<String, Object>> resume() {
    String org = TenantContext.require().organizationId();
    return ApiResponse.ok(lifecycleService.resume(org));
  }
}
