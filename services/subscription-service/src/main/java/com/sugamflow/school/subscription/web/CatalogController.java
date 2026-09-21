package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.subscription.service.CatalogService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform catalog APIs. Reads for discovery; PUT upserts for Super Admin configuration. Does not
 * change entitlements / feature-flag contracts.
 */
@RestController
@RequestMapping("/api/subscription/catalog")
public class CatalogController {

  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/business-types")
  public ApiResponse<List<Map<String, Object>>> businessTypes() {
    return ApiResponse.ok(catalogService.listBusinessTypes());
  }

  @PutMapping("/business-types")
  public ApiResponse<Map<String, Object>> upsertBusinessType(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(catalogService.upsertBusinessType(body));
  }

  @GetMapping("/modules")
  public ApiResponse<List<Map<String, Object>>> modules(
      @RequestParam(value = "businessType", required = false) String businessType) {
    return ApiResponse.ok(catalogService.listModules(businessType));
  }

  @PutMapping("/modules")
  public ApiResponse<Map<String, Object>> upsertModule(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(catalogService.upsertModule(body));
  }

  @GetMapping("/features")
  public ApiResponse<List<Map<String, Object>>> features(
      @RequestParam(value = "module", required = false) String module) {
    return ApiResponse.ok(catalogService.listFeatures(module));
  }

  @PutMapping("/features")
  public ApiResponse<Map<String, Object>> upsertFeature(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(catalogService.upsertFeature(body));
  }

  @GetMapping("/limits")
  public ApiResponse<List<Map<String, Object>>> limits(
      @RequestParam(value = "businessType", required = false) String businessType) {
    return ApiResponse.ok(catalogService.listLimits(businessType));
  }

  @PutMapping("/limits")
  public ApiResponse<Map<String, Object>> upsertLimit(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(catalogService.upsertLimit(body));
  }
}
