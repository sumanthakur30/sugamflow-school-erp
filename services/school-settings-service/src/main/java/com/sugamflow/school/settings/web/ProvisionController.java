package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.settings.service.SchoolProvisionerService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ProvisionController {

  private final SchoolProvisionerService provisioner;

  public ProvisionController(SchoolProvisionerService provisioner) {
    this.provisioner = provisioner;
  }

  /**
   * Idempotent school bootstrap after first authenticated login. Optional body: {@code schoolName}
   * (from shop registry). Safe to call on every login.
   */
  @PostMapping("/provision")
  public ApiResponse<Map<String, Object>> provision(
      @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(provisioner.provision(body == null ? Map.of() : body));
  }
}
