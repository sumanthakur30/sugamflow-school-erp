package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.subscription.model.SubscriptionPlan;
import com.sugamflow.school.subscription.service.SubscriptionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

  private final SubscriptionService subscriptionService;

  public SubscriptionController(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  @GetMapping("/plans")
  public ApiResponse<List<SubscriptionPlan>> listPlans() {
    return ApiResponse.ok(subscriptionService.listPlans());
  }

  @GetMapping("/plans/{planId}")
  public ApiResponse<SubscriptionPlan> getPlan(@PathVariable("planId") String planId) {
    return ApiResponse.ok(subscriptionService.getPlan(planId));
  }

  @PostMapping("/plans")
  public ApiResponse<SubscriptionPlan> create(@RequestBody SubscriptionPlan plan) {
    return ApiResponse.ok(subscriptionService.savePlan(plan));
  }

  @PutMapping("/plans/{planId}")
  public ApiResponse<SubscriptionPlan> update(
      @PathVariable("planId") String planId, @RequestBody SubscriptionPlan plan) {
    plan.setId(planId);
    return ApiResponse.ok(subscriptionService.savePlan(plan));
  }

  @PutMapping("/tenants/current/plan/{planId}")
  public ApiResponse<Map<String, Object>> assign(@PathVariable("planId") String planId) {
    String org = TenantContext.require().organizationId();
    return ApiResponse.ok(subscriptionService.assignPlan(org, planId));
  }

  @GetMapping("/tenants/current/entitlements")
  public ApiResponse<Map<String, Object>> entitlements() {
    String org = TenantContext.require().organizationId();
    return ApiResponse.ok(subscriptionService.entitlements(org));
  }

  @GetMapping("/feature-flags/{flag}")
  public ApiResponse<Map<String, Object>> flag(@PathVariable("flag") String flag) {
    String org = TenantContext.require().organizationId();
    return ApiResponse.ok(subscriptionService.flag(org, flag));
  }
}
