package com.sugamflow.school.compliance.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.compliance.dto.ComplianceDashboardResponse;
import com.sugamflow.school.compliance.dto.ComplianceProfileRequest;
import com.sugamflow.school.compliance.dto.ComplianceProfileResponse;
import com.sugamflow.school.compliance.dto.ReadinessResponse;
import com.sugamflow.school.compliance.dto.ValidateRunRequest;
import com.sugamflow.school.compliance.dto.ValidateRunResponse;
import com.sugamflow.school.compliance.dto.ValidationFindingResponse;
import com.sugamflow.school.compliance.service.ComplianceService;
import com.sugamflow.school.compliance.service.ValidationEngineService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {
  private final ComplianceService complianceService;
  private final ValidationEngineService validationEngineService;

  public ComplianceController(
      ComplianceService complianceService, ValidationEngineService validationEngineService) {
    this.complianceService = complianceService;
    this.validationEngineService = validationEngineService;
  }

  @GetMapping("/dashboard")
  public ApiResponse<ComplianceDashboardResponse> dashboard() {
    return ApiResponse.ok(complianceService.dashboard());
  }

  @GetMapping("/profile")
  public ApiResponse<ComplianceProfileResponse> getProfile() {
    return ApiResponse.ok(complianceService.getProfile());
  }

  @PutMapping("/profile")
  public ApiResponse<ComplianceProfileResponse> upsertProfile(
      @Valid @RequestBody ComplianceProfileRequest request) {
    return ApiResponse.ok(complianceService.upsertProfile(request));
  }

  @GetMapping("/readiness")
  public ApiResponse<ReadinessResponse> readiness() {
    return ApiResponse.ok(validationEngineService.readiness());
  }

  @GetMapping("/findings")
  public ApiResponse<PageResult<ValidationFindingResponse>> findings(
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) String severity,
      @RequestParam(required = false) Long campaignId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(
        validationEngineService.findings(entityType, severity, campaignId, page, size));
  }

  @PostMapping("/campaigns/validate")
  public ApiResponse<ValidateRunResponse> validateLatest(
      @RequestBody(required = false) ValidateRunRequest request) {
    return ApiResponse.ok(validationEngineService.validateLatestOrCreate(request));
  }

  @PostMapping("/campaigns/{id}/validate")
  public ApiResponse<ValidateRunResponse> validateCampaign(@PathVariable("id") Long id) {
    return ApiResponse.ok(validationEngineService.validateCampaign(id));
  }
}
