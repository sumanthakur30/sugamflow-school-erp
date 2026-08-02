package com.sugamflow.school.admission.web;

import com.sugamflow.school.admission.service.AdmissionApplicationService;
import com.sugamflow.school.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated online admission from the School Website Platform. Organization must already be
 * resolved by website host mapping on the client; server still validates feature flags.
 */
@RestController
@RequestMapping("/api/admission/public")
public class AdmissionPublicController {

  private final AdmissionApplicationService service;

  public AdmissionPublicController(AdmissionApplicationService service) {
    this.service = service;
  }

  @PostMapping("/apply")
  public ApiResponse<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.submitFromWebsite(body));
  }
}
