package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.subscription.service.SubscriptionAnalyticsService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 10 admin analytics (read-only). */
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionAnalyticsController {

  private final SubscriptionAnalyticsService analyticsService;

  public SubscriptionAnalyticsController(SubscriptionAnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping({"/analytics/dashboard", "/subscriptions/analytics/dashboard"})
  public ApiResponse<Map<String, Object>> dashboard() {
    return ApiResponse.ok(analyticsService.dashboard());
  }

  @GetMapping({"/analytics/overview", "/subscriptions/analytics/overview"})
  public ApiResponse<Map<String, Object>> overview() {
    return ApiResponse.ok(analyticsService.overview());
  }

  @GetMapping({"/analytics/plan-mix", "/subscriptions/analytics/plan-mix"})
  public ApiResponse<List<Map<String, Object>>> planMix() {
    return ApiResponse.ok(analyticsService.planMix());
  }

  @GetMapping({"/analytics/renewals", "/subscriptions/analytics/renewals"})
  public ApiResponse<Map<String, Object>> renewals(
      @RequestParam(defaultValue = "30") int withinDays) {
    return ApiResponse.ok(analyticsService.renewals(withinDays));
  }

  @GetMapping({"/analytics/revenue", "/subscriptions/analytics/revenue"})
  public ApiResponse<Map<String, Object>> revenue(@RequestParam(defaultValue = "6") int months) {
    return ApiResponse.ok(analyticsService.revenue(months));
  }

  @GetMapping({"/analytics/usage", "/subscriptions/analytics/usage"})
  public ApiResponse<Map<String, Object>> usage(@RequestParam(defaultValue = "25") int limit) {
    return ApiResponse.ok(analyticsService.usageHeatmap(limit));
  }
}
