package com.sugamflow.school.compliance.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.compliance.dto.DisclosurePackageResponse;
import com.sugamflow.school.compliance.dto.DisclosurePublishRequest;
import com.sugamflow.school.compliance.service.DisclosureService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compliance")
public class DisclosureController {
  private final DisclosureService disclosureService;

  public DisclosureController(DisclosureService disclosureService) {
    this.disclosureService = disclosureService;
  }

  @GetMapping("/disclosure/preview")
  public ApiResponse<DisclosurePackageResponse> preview() {
    return ApiResponse.ok(disclosureService.preview());
  }

  @PostMapping("/disclosure/publish")
  public ApiResponse<DisclosurePackageResponse> publish(
      @RequestBody(required = false) @Valid DisclosurePublishRequest request) {
    return ApiResponse.ok(disclosureService.publish(request));
  }

  @GetMapping("/public/disclosure/{organizationId}")
  public ApiResponse<DisclosurePackageResponse> publicDisclosure(
      @PathVariable("organizationId") String organizationId) {
    return ApiResponse.ok(disclosureService.publicDisclosure(organizationId));
  }
}
