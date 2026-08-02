package com.sugamflow.school.website.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.website.persistence.entity.WebsiteDomain;
import com.sugamflow.school.website.persistence.entity.WebsiteSite;
import com.sugamflow.school.website.service.WebsiteResolveService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated admin APIs (Phase 0 read-only bootstrap). */
@RestController
@RequestMapping("/api/website/admin")
public class WebsiteAdminController {

  private final WebsiteResolveService resolveService;

  public WebsiteAdminController(WebsiteResolveService resolveService) {
    this.resolveService = resolveService;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    String organizationId = TenantContext.require().organizationId();
    if (organizationId == null || organizationId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-Id required");
    }

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
}
