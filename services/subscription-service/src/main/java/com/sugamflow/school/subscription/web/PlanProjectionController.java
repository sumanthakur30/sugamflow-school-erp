package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.subscription.service.PlanProjectionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only views of dual-written plan composition. Entitlements still resolve from plan JSON.
 */
@RestController
@RequestMapping("/api/subscription/plans/{planId}/projection")
public class PlanProjectionController {

  private final PlanProjectionService planProjectionService;

  public PlanProjectionController(PlanProjectionService planProjectionService) {
    this.planProjectionService = planProjectionService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> projection(@PathVariable("planId") String planId) {
    return ApiResponse.ok(planProjectionService.getProjection(planId));
  }

  @GetMapping("/features")
  public ApiResponse<List<Map<String, Object>>> features(@PathVariable("planId") String planId) {
    return ApiResponse.ok(planProjectionService.listFeatures(planId));
  }

  @GetMapping("/limits")
  public ApiResponse<List<Map<String, Object>>> limits(@PathVariable("planId") String planId) {
    return ApiResponse.ok(planProjectionService.listLimits(planId));
  }

  @GetMapping("/modules")
  public ApiResponse<List<Map<String, Object>>> modules(@PathVariable("planId") String planId) {
    return ApiResponse.ok(planProjectionService.listModules(planId));
  }
}
