package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.subscription.config.SchoolBillingProperties;
import com.sugamflow.school.subscription.service.SchoolStudentBillingJob;
import com.sugamflow.school.subscription.service.SchoolStudentBillingService;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/subscription")
public class SchoolStudentBillingController {

  private final SchoolStudentBillingService billing;
  private final SchoolStudentBillingJob job;
  private final SchoolBillingProperties properties;

  public SchoolStudentBillingController(
      SchoolStudentBillingService billing,
      SchoolStudentBillingJob job,
      SchoolBillingProperties properties) {
    this.billing = billing;
    this.job = job;
    this.properties = properties;
  }

  @GetMapping({"/billing/school/summary", "/subscriptions/billing/school/summary"})
  public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) String period) {
    requirePlatform();
    return ApiResponse.ok(billing.summary(period(period)));
  }

  @GetMapping({"/billing/school/runs", "/subscriptions/billing/school/runs"})
  public ApiResponse<List<Map<String, Object>>> runs(@RequestParam(required = false) String period) {
    requirePlatform();
    return ApiResponse.ok(billing.runsForPeriod(period(period)));
  }

  @GetMapping({
    "/billing/school/organizations/{organizationId}",
    "/subscriptions/billing/school/organizations/{organizationId}"
  })
  public ApiResponse<Map<String, Object>> organization(@PathVariable String organizationId) {
    requirePlatform();
    return ApiResponse.ok(billing.profile(organizationId));
  }

  /** School billing user: only the authenticated organization. */
  @GetMapping({"/billing/school/current", "/subscriptions/billing/school/current"})
  public ApiResponse<Map<String, Object>> current() {
    TenantScope scope = TenantContext.require();
    String organizationId = scope.organizationId();
    if (organizationId == null || organizationId.isBlank() || "PLATFORM".equalsIgnoreCase(organizationId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "School organization is required");
    }
    return ApiResponse.ok(billing.profile(organizationId));
  }

  @PutMapping({"/billing/school/contracts", "/subscriptions/billing/school/contracts"})
  public ApiResponse<Map<String, Object>> saveContract(@RequestBody Map<String, Object> body) {
    requirePlatform();
    return ApiResponse.ok(billing.saveContract(body));
  }

  @PostMapping({"/billing/school/runs", "/subscriptions/billing/school/runs"})
  public ApiResponse<List<Map<String, Object>>> runAll(@RequestParam(required = false) String period) {
    requirePlatform();
    return ApiResponse.ok(job.runPeriod(period(period)));
  }

  @PostMapping({
    "/billing/school/organizations/{organizationId}/runs",
    "/subscriptions/billing/school/organizations/{organizationId}/runs"
  })
  public ApiResponse<Map<String, Object>> runOne(
      @PathVariable String organizationId, @RequestParam(required = false) String period) {
    requirePlatform();
    return ApiResponse.ok(billing.billOrganization(organizationId, period(period)));
  }

  private YearMonth period(String period) {
    return SchoolStudentBillingService.periodOrCurrent(period, ZoneId.of(properties.getZone()));
  }

  private static void requirePlatform() {
    TenantScope scope = TenantContext.require();
    String role = scope.roleCode() == null ? "" : scope.roleCode();
    if ("SUPER_ADMIN".equalsIgnoreCase(role) || "PLATFORM".equalsIgnoreCase(scope.organizationId())) {
      return;
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator required");
  }
}
