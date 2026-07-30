package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.subscription.service.RenewalCrmService;
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

/** Phase 12 CRM renewals APIs. */
@RestController
@RequestMapping("/api/subscription")
public class RenewalCrmController {

  private final RenewalCrmService renewalCrmService;

  public RenewalCrmController(RenewalCrmService renewalCrmService) {
    this.renewalCrmService = renewalCrmService;
  }

  @GetMapping({"/crm/dashboard", "/subscriptions/crm/dashboard"})
  public ApiResponse<Map<String, Object>> dashboard() {
    return ApiResponse.ok(renewalCrmService.dashboard());
  }

  @PostMapping({"/crm/sync", "/subscriptions/crm/sync"})
  public ApiResponse<Map<String, Object>> sync() {
    return ApiResponse.ok(renewalCrmService.syncPipeline());
  }

  @GetMapping({"/crm/pipeline", "/subscriptions/crm/pipeline"})
  public ApiResponse<List<Map<String, Object>>> pipeline(
      @RequestParam(defaultValue = "30") int withinDays,
      @RequestParam(required = false) String stage) {
    return ApiResponse.ok(renewalCrmService.pipeline(withinDays, stage));
  }

  @GetMapping({
    "/crm/tenants/{organizationId}/health",
    "/subscriptions/crm/tenants/{organizationId}/health"
  })
  public ApiResponse<Map<String, Object>> health(@PathVariable String organizationId) {
    return ApiResponse.ok(renewalCrmService.health(organizationId));
  }

  @GetMapping({
    "/crm/tenants/{organizationId}/upsell",
    "/subscriptions/crm/tenants/{organizationId}/upsell"
  })
  public ApiResponse<List<Map<String, Object>>> upsell(@PathVariable String organizationId) {
    return ApiResponse.ok(renewalCrmService.upsellHints(organizationId));
  }

  @PutMapping({
    "/crm/tenants/{organizationId}/opportunity",
    "/subscriptions/crm/tenants/{organizationId}/opportunity"
  })
  public ApiResponse<Map<String, Object>> updateOpportunity(
      @PathVariable String organizationId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(renewalCrmService.updateOpportunity(organizationId, body));
  }

  @PostMapping({"/crm/reminders/generate", "/subscriptions/crm/reminders/generate"})
  public ApiResponse<Map<String, Object>> generateReminders() {
    return ApiResponse.ok(renewalCrmService.generateReminders());
  }

  @GetMapping({"/crm/reminders", "/subscriptions/crm/reminders"})
  public ApiResponse<List<Map<String, Object>>> reminders(
      @RequestParam(defaultValue = "PENDING") String status,
      @RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.ok(renewalCrmService.listReminders(status, limit));
  }

  @PostMapping({
    "/crm/reminders/{reminderId}/status",
    "/subscriptions/crm/reminders/{reminderId}/status"
  })
  public ApiResponse<Map<String, Object>> markReminder(
      @PathVariable long reminderId, @RequestBody(required = false) Map<String, Object> body) {
    String status =
        body == null || body.get("status") == null ? "SENT" : String.valueOf(body.get("status"));
    return ApiResponse.ok(renewalCrmService.markReminder(reminderId, status));
  }
}
