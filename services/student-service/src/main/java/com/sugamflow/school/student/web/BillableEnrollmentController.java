package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.service.BillableEnrollmentService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billable enrollment for the caller's organization only. The organization id comes from the tenant
 * header, not from a query parameter.
 */
@RestController
@RequestMapping("/api/student/billing")
public class BillableEnrollmentController {

  private final BillableEnrollmentService service;

  public BillableEnrollmentController(BillableEnrollmentService service) {
    this.service = service;
  }

  @GetMapping("/billable-count")
  public ApiResponse<Map<String, Object>> billableCount() {
    return ApiResponse.ok(service.countForCurrentTenant());
  }
}
