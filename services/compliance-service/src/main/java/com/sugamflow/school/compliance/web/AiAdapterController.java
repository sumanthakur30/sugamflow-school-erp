package com.sugamflow.school.compliance.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.compliance.dto.AdapterJobResponse;
import com.sugamflow.school.compliance.dto.AiScanJobResponse;
import com.sugamflow.school.compliance.service.AdapterJobService;
import com.sugamflow.school.compliance.service.AiWarnScanService;

@RestController
@RequestMapping("/api/compliance")
public class AiAdapterController {
  private final AiWarnScanService aiWarnScanService;
  private final AdapterJobService adapterJobService;

  public AiAdapterController(
      AiWarnScanService aiWarnScanService, AdapterJobService adapterJobService) {
    this.aiWarnScanService = aiWarnScanService;
    this.adapterJobService = adapterJobService;
  }

  @PostMapping("/campaigns/{id}/ai-scan")
  public ApiResponse<AiScanJobResponse> startAiScan(@PathVariable("id") Long id) {
    return ApiResponse.ok(aiWarnScanService.start(id));
  }

  @GetMapping("/ai-scans/{jobId}")
  public ApiResponse<AiScanJobResponse> getAiScan(@PathVariable Long jobId) {
    return ApiResponse.ok(aiWarnScanService.get(jobId));
  }

  @GetMapping("/ai-scans")
  public ApiResponse<List<AiScanJobResponse>> listAiScans(
      @RequestParam(required = false) Long campaignId) {
    return ApiResponse.ok(aiWarnScanService.list(campaignId));
  }

  @GetMapping("/adapter-jobs")
  public ApiResponse<List<AdapterJobResponse>> listAdapterJobs(
      @RequestParam(required = false) Long campaignId) {
    return ApiResponse.ok(adapterJobService.list(campaignId));
  }
}
