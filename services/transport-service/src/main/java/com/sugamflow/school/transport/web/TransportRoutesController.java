package com.sugamflow.school.transport.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.transport.service.TransportAssignmentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport/routes")
public class TransportRoutesController {

  private final TransportAssignmentService service;

  public TransportRoutesController(TransportAssignmentService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.listRoutes());
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> upsert(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.upsertRoute(body));
  }

  @GetMapping("/assignments")
  public ApiResponse<?> active(
      @RequestParam(value = "admissionNo", required = false) String admissionNo) {
    if (admissionNo != null && !admissionNo.isBlank()) {
      return ApiResponse.ok(service.activeAssignmentForAdmission(admissionNo));
    }
    return ApiResponse.ok(service.activeAssignments());
  }

  @PostMapping("/assign")
  public ApiResponse<Map<String, Object>> assign(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.assign(body));
  }

  @PostMapping("/assignments/{id}/end")
  public ApiResponse<Map<String, Object>> end(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.end(id));
  }
}
