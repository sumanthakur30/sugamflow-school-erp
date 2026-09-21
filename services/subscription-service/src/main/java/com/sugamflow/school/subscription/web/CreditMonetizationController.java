package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.subscription.service.CreditMonetizationService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 13 AI / credit monetization APIs. */
@RestController
@RequestMapping("/api/subscription")
public class CreditMonetizationController {

  private final CreditMonetizationService creditMonetizationService;

  public CreditMonetizationController(CreditMonetizationService creditMonetizationService) {
    this.creditMonetizationService = creditMonetizationService;
  }

  @GetMapping({"/credits/policies", "/subscriptions/credits/policies"})
  public ApiResponse<List<Map<String, Object>>> policies() {
    return ApiResponse.ok(creditMonetizationService.listPolicies());
  }

  @PutMapping({"/credits/policies", "/subscriptions/credits/policies"})
  public ApiResponse<Map<String, Object>> upsertPolicy(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(creditMonetizationService.upsertPolicy(body));
  }

  @GetMapping({"/tenants/current/credits/wallets", "/subscriptions/credits/wallets"})
  public ApiResponse<List<Map<String, Object>>> wallets() {
    return ApiResponse.ok(creditMonetizationService.listWallets(org()));
  }

  @GetMapping({"/tenants/current/credits/ledger", "/subscriptions/credits/ledger"})
  public ApiResponse<List<Map<String, Object>>> ledger(
      @RequestParam(required = false) String meterCode,
      @RequestParam(defaultValue = "50") int limit) {
    return ApiResponse.ok(creditMonetizationService.listLedger(org(), meterCode, limit));
  }

  @GetMapping({"/tenants/current/credits/period-runs", "/subscriptions/credits/period-runs"})
  public ApiResponse<List<Map<String, Object>>> periodRuns() {
    return ApiResponse.ok(creditMonetizationService.listPeriodRuns(org()));
  }

  @PostMapping({"/tenants/current/credits/consume", "/subscriptions/credits/consume"})
  public ApiResponse<Map<String, Object>> consume(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(creditMonetizationService.consume(org(), body));
  }

  @PostMapping({"/tenants/current/credits/grant", "/subscriptions/credits/grant"})
  public ApiResponse<Map<String, Object>> grant(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(creditMonetizationService.grant(org(), body));
  }

  @GetMapping({
    "/tenants/current/credits/carry-forward/preview",
    "/subscriptions/credits/carry-forward/preview"
  })
  public ApiResponse<Map<String, Object>> preview(@RequestParam String meterCode) {
    return ApiResponse.ok(creditMonetizationService.previewCarryForward(org(), meterCode));
  }

  @PostMapping({
    "/tenants/current/credits/carry-forward",
    "/subscriptions/credits/carry-forward"
  })
  public ApiResponse<Map<String, Object>> carryForward(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(creditMonetizationService.runCarryForward(org(), body));
  }

  /** Super Admin: run carry-forward for all wallets of a meter. */
  @PostMapping({"/credits/carry-forward/batch", "/subscriptions/credits/carry-forward/batch"})
  public ApiResponse<Map<String, Object>> carryForwardBatch(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(creditMonetizationService.runCarryForwardBatch(body));
  }

  /** Super Admin: operate on a specific org via path (X-Tenant-Id may still be PLATFORM). */
  @PostMapping({
    "/credits/tenants/{organizationId}/consume",
    "/subscriptions/credits/tenants/{organizationId}/consume"
  })
  public ApiResponse<Map<String, Object>> consumeForOrg(
      @PathVariable String organizationId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(creditMonetizationService.consume(organizationId, body));
  }

  @PostMapping({
    "/credits/tenants/{organizationId}/grant",
    "/subscriptions/credits/tenants/{organizationId}/grant"
  })
  public ApiResponse<Map<String, Object>> grantForOrg(
      @PathVariable String organizationId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(creditMonetizationService.grant(organizationId, body));
  }

  @PostMapping({
    "/credits/tenants/{organizationId}/carry-forward",
    "/subscriptions/credits/tenants/{organizationId}/carry-forward"
  })
  public ApiResponse<Map<String, Object>> carryForwardForOrg(
      @PathVariable String organizationId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(creditMonetizationService.runCarryForward(organizationId, body));
  }

  @GetMapping({
    "/credits/tenants/{organizationId}/wallets",
    "/subscriptions/credits/tenants/{organizationId}/wallets"
  })
  public ApiResponse<List<Map<String, Object>>> walletsForOrg(@PathVariable String organizationId) {
    return ApiResponse.ok(creditMonetizationService.listWallets(organizationId));
  }

  private static String org() {
    return TenantContext.require().organizationId();
  }
}
