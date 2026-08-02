package com.sugamflow.school.website.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.website.persistence.entity.WebsiteDomain;
import com.sugamflow.school.website.persistence.entity.WebsiteSite;
import com.sugamflow.school.website.service.WebsiteResolveService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated admin APIs for website shell config (Phase 3 builder). */
@RestController
@RequestMapping("/api/website/admin")
public class WebsiteAdminController {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};

  private final WebsiteResolveService resolveService;
  private final ObjectMapper objectMapper;

  public WebsiteAdminController(WebsiteResolveService resolveService, ObjectMapper objectMapper) {
    this.resolveService = resolveService;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    String organizationId = orgId();
    WebsiteSite site =
        resolveService
            .findSite(organizationId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No website configured for this organization"));

    List<WebsiteDomain> domains = resolveService.listDomains(organizationId);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("organizationId", site.getOrganizationId());
    payload.put("status", site.getStatus());
    payload.put("templateCode", site.getTemplateCode());
    payload.put("displayName", site.getDisplayName());
    payload.put("erpLoginUrl", site.getErpLoginUrl());
    payload.put("homepage", readJsonList(site.getHomepageJson()));
    payload.put("theme", readJsonMap(site.getThemeJson()));
    payload.put("seo", readJsonMap(site.getSeoJson()));
    payload.put(
        "domains",
        domains.stream()
            .map(
                d -> {
                  Map<String, Object> row = new LinkedHashMap<>();
                  row.put("host", d.getHost());
                  row.put("primary", d.isPrimary());
                  row.put("status", d.getStatus());
                  row.put("sslStatus", d.getSslStatus());
                  return row;
                })
            .toList());
    return ApiResponse.ok(payload);
  }

  @PutMapping("/homepage")
  public ApiResponse<List<Map<String, Object>>> updateHomepage(
      @RequestBody List<Map<String, Object>> sections) {
    return ApiResponse.ok(resolveService.updateHomepage(orgId(), sections));
  }

  @PutMapping("/theme")
  public ApiResponse<Map<String, Object>> updateTheme(@RequestBody Map<String, Object> theme) {
    return ApiResponse.ok(resolveService.updateTheme(orgId(), theme));
  }

  @PutMapping("/seo")
  public ApiResponse<Map<String, Object>> updateSeo(@RequestBody Map<String, Object> seo) {
    return ApiResponse.ok(resolveService.updateSeo(orgId(), seo));
  }

  private static String orgId() {
    String organizationId = TenantContext.require().organizationId();
    if (organizationId == null || organizationId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id required");
    }
    return organizationId;
  }

  private List<Map<String, Object>> readJsonList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, LIST_TYPE);
    } catch (Exception ex) {
      return List.of();
    }
  }

  private Map<String, Object> readJsonMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Collections.emptyMap();
      }
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception ex) {
      return Collections.emptyMap();
    }
  }
}
