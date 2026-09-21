package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.subscription.service.UsageMeteringService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional usage / validation APIs. School UI/services may ignore these; entitlements stay JSON-based.
 */
@RestController
@RequestMapping("/api/subscription")
public class UsageMeteringController {

  private final UsageMeteringService usageMeteringService;

  public UsageMeteringController(UsageMeteringService usageMeteringService) {
    this.usageMeteringService = usageMeteringService;
  }

  @GetMapping({"/tenants/current/usage", "/subscriptions/usage"})
  public ApiResponse<Map<String, Object>> usage() {
    return ApiResponse.ok(usageMeteringService.listUsage(org()));
  }

  @GetMapping({"/tenants/current/limits", "/subscriptions/limits"})
  public ApiResponse<Map<String, Object>> limits() {
    return ApiResponse.ok(usageMeteringService.listLimitsWithUsage(org()));
  }

  @GetMapping("/tenants/current/usage/events")
  public ApiResponse<List<Map<String, Object>>> events(
      @RequestParam(value = "limitCode", required = false) String limitCode) {
    return ApiResponse.ok(usageMeteringService.recentEvents(org(), limitCode));
  }

  @PostMapping({"/subscriptions/validate-limit", "/tenants/current/validate-limit"})
  public ApiResponse<Map<String, Object>> validateLimit(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(usageMeteringService.validateLimit(org(), body));
  }

  @PostMapping({"/subscriptions/validate-feature", "/tenants/current/validate-feature"})
  public ApiResponse<Map<String, Object>> validateFeature(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(usageMeteringService.validateFeature(org(), body));
  }

  @PostMapping({"/subscriptions/increment-usage", "/tenants/current/increment-usage"})
  public ApiResponse<Map<String, Object>> increment(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(usageMeteringService.incrementUsage(org(), body, actor()));
  }

  @PostMapping({"/subscriptions/decrement-usage", "/tenants/current/decrement-usage"})
  public ApiResponse<Map<String, Object>> decrement(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(usageMeteringService.decrementUsage(org(), body, actor()));
  }

  private static String org() {
    return TenantContext.require().organizationId();
  }

  private static String actor() {
    try {
      return TenantContext.require().userId();
    } catch (RuntimeException ignored) {
      return null;
    }
  }
}
