package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.subscription.service.RateCardService;
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

/** Super Admin rate card. Does not assign plans or change entitlements. */
@RestController
@RequestMapping("/api/subscription/pricing")
public class RateCardController {

  private final RateCardService rateCardService;

  public RateCardController(RateCardService rateCardService) {
    this.rateCardService = rateCardService;
  }

  @GetMapping("/dashboard")
  public ApiResponse<Map<String, Object>> dashboard() {
    return ApiResponse.ok(rateCardService.dashboard());
  }

  @GetMapping("/rates")
  public ApiResponse<List<Map<String, Object>>> rates(
      @RequestParam(value = "open", defaultValue = "true") boolean open,
      @RequestParam(value = "addons", defaultValue = "false") boolean addons) {
    return ApiResponse.ok(rateCardService.listRates(open, addons));
  }

  @PutMapping("/rates")
  public ApiResponse<Map<String, Object>> reviseRate(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(rateCardService.reviseRate(body));
  }

  @GetMapping("/packages")
  public ApiResponse<List<Map<String, Object>>> packages(
      @RequestParam(value = "open", defaultValue = "true") boolean open) {
    return ApiResponse.ok(rateCardService.listPackages(open));
  }

  @PutMapping("/packages")
  public ApiResponse<Map<String, Object>> revisePackage(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(rateCardService.revisePackage(body));
  }

  @PostMapping("/proposals/preview")
  public ApiResponse<Map<String, Object>> previewProposal(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(rateCardService.previewProposal(body));
  }

  @GetMapping("/plans/{planId}/value")
  public ApiResponse<Map<String, Object>> valuePlan(
      @PathVariable String planId,
      @RequestParam(value = "businessType", required = false) String businessType,
      @RequestParam(value = "cycle", defaultValue = "MONTHLY") String cycle) {
    return ApiResponse.ok(rateCardService.valuePlan(planId, businessType, cycle));
  }

  @PutMapping("/plans/{planId}/discount")
  public ApiResponse<Map<String, Object>> discount(
      @PathVariable String planId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(rateCardService.revisePlanDiscount(planId, body));
  }

  @GetMapping("/discounts")
  public ApiResponse<List<Map<String, Object>>> discounts() {
    return ApiResponse.ok(rateCardService.listDiscounts());
  }

  @GetMapping("/quotes")
  public ApiResponse<List<Map<String, Object>>> quotes() {
    return ApiResponse.ok(rateCardService.listQuotes());
  }

  @PostMapping("/quotes")
  public ApiResponse<Map<String, Object>> createQuote(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(rateCardService.createQuote(body));
  }

  @PostMapping("/quotes/{id}/status")
  public ApiResponse<Map<String, Object>> quoteStatus(
      @PathVariable long id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(rateCardService.setQuoteStatus(id, body));
  }
}
