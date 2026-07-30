package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.subscription.service.PlanBuilderService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phase 11 plan builder APIs (versions, publish, scheduled prices). */
@RestController
@RequestMapping("/api/subscription")
public class PlanBuilderController {

  private final PlanBuilderService planBuilderService;

  public PlanBuilderController(PlanBuilderService planBuilderService) {
    this.planBuilderService = planBuilderService;
  }

  @GetMapping({"/plans/{planId}/versions", "/subscriptions/plans/{planId}/versions"})
  public ApiResponse<List<Map<String, Object>>> listVersions(@PathVariable String planId) {
    return ApiResponse.ok(planBuilderService.listVersions(planId));
  }

  @GetMapping({
    "/plans/{planId}/versions/{versionId}",
    "/subscriptions/plans/{planId}/versions/{versionId}"
  })
  public ApiResponse<Map<String, Object>> getVersion(
      @PathVariable String planId, @PathVariable long versionId) {
    return ApiResponse.ok(planBuilderService.getVersion(planId, versionId));
  }

  @PostMapping({
    "/plans/{planId}/versions/draft",
    "/subscriptions/plans/{planId}/versions/draft"
  })
  public ApiResponse<Map<String, Object>> ensureOrCreateDraft(
      @PathVariable String planId, @RequestBody(required = false) Map<String, Object> body) {
    if (body != null && Boolean.TRUE.equals(body.get("forceNew"))) {
      return ApiResponse.ok(planBuilderService.createDraft(planId, body));
    }
    return ApiResponse.ok(planBuilderService.ensureDraft(planId));
  }

  @PutMapping({
    "/plans/{planId}/versions/{versionId}",
    "/subscriptions/plans/{planId}/versions/{versionId}"
  })
  public ApiResponse<Map<String, Object>> saveDraft(
      @PathVariable String planId,
      @PathVariable long versionId,
      @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(planBuilderService.saveDraft(planId, versionId, body));
  }

  @PostMapping({"/plans/{planId}/publish", "/subscriptions/plans/{planId}/publish"})
  public ApiResponse<Map<String, Object>> publish(
      @PathVariable String planId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(planBuilderService.publish(planId, body == null ? Map.of() : body));
  }

  @GetMapping({
    "/plans/{planId}/price-schedules",
    "/subscriptions/plans/{planId}/price-schedules"
  })
  public ApiResponse<List<Map<String, Object>>> listSchedules(@PathVariable String planId) {
    return ApiResponse.ok(planBuilderService.listPriceSchedules(planId));
  }

  @PostMapping({
    "/plans/{planId}/price-schedules",
    "/subscriptions/plans/{planId}/price-schedules"
  })
  public ApiResponse<Map<String, Object>> schedulePrice(
      @PathVariable String planId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(planBuilderService.schedulePrice(planId, body));
  }

  @PostMapping({
    "/plans/{planId}/price-schedules/{scheduleId}/cancel",
    "/subscriptions/plans/{planId}/price-schedules/{scheduleId}/cancel"
  })
  public ApiResponse<Map<String, Object>> cancelSchedule(
      @PathVariable String planId, @PathVariable long scheduleId) {
    return ApiResponse.ok(planBuilderService.cancelPriceSchedule(planId, scheduleId));
  }

  @PostMapping({
    "/billing/price-schedules/apply-due",
    "/subscriptions/billing/price-schedules/apply-due"
  })
  public ApiResponse<Map<String, Object>> applyDue() {
    return ApiResponse.ok(planBuilderService.applyDueSchedules());
  }
}
