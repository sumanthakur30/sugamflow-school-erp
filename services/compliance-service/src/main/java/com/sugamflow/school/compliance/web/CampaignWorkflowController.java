package com.sugamflow.school.compliance.web;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.compliance.dto.ApprovalDecisionRequest;
import com.sugamflow.school.compliance.dto.CampaignResponse;
import com.sugamflow.school.compliance.dto.CreateCampaignRequest;
import com.sugamflow.school.compliance.dto.ExportRequest;
import com.sugamflow.school.compliance.dto.SubmitCampaignRequest;
import com.sugamflow.school.compliance.service.CampaignWorkflowService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compliance/campaigns")
public class CampaignWorkflowController {
  private final CampaignWorkflowService workflowService;

  public CampaignWorkflowController(CampaignWorkflowService workflowService) {
    this.workflowService = workflowService;
  }

  @GetMapping
  public ApiResponse<List<CampaignResponse>> list() {
    return ApiResponse.ok(workflowService.list());
  }

  @GetMapping("/{id}")
  public ApiResponse<CampaignResponse> get(@PathVariable Long id) {
    return ApiResponse.ok(workflowService.get(id));
  }

  @PostMapping
  public ApiResponse<CampaignResponse> create(@Valid @RequestBody CreateCampaignRequest request) {
    return ApiResponse.ok(workflowService.create(request));
  }

  @PostMapping("/{id}/submit-for-review")
  public ApiResponse<CampaignResponse> submitForReview(@PathVariable Long id) {
    return ApiResponse.ok(workflowService.submitForReview(id));
  }

  @PostMapping("/{id}/approve")
  public ApiResponse<CampaignResponse> approve(
      @PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest request) {
    return ApiResponse.ok(workflowService.decide(id, request));
  }

  @PostMapping("/{id}/lock")
  public ApiResponse<CampaignResponse> lock(@PathVariable Long id) {
    return ApiResponse.ok(workflowService.lock(id));
  }

  @PostMapping("/{id}/export")
  public ApiResponse<CampaignResponse> export(
      @PathVariable Long id, @RequestBody(required = false) ExportRequest request) {
    return ApiResponse.ok(workflowService.export(id, request));
  }

  @PostMapping("/{id}/submit")
  public ApiResponse<CampaignResponse> submit(
      @PathVariable Long id, @RequestBody(required = false) SubmitCampaignRequest request) {
    return ApiResponse.ok(workflowService.submit(id, request));
  }

  @PostMapping("/{id}/archive")
  public ApiResponse<CampaignResponse> archive(@PathVariable Long id) {
    return ApiResponse.ok(workflowService.archive(id));
  }

  @GetMapping("/{id}/artifacts/{artifactId}/download")
  public ResponseEntity<org.springframework.core.io.Resource> download(
      @PathVariable Long id, @PathVariable Long artifactId) throws IOException {
    CampaignWorkflowService.FilePayload payload = workflowService.downloadArtifact(id, artifactId);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + payload.fileName().replace("\"", "") + "\"")
        .contentType(MediaType.parseMediaType(payload.contentType()))
        .body(payload.resource());
  }
}
