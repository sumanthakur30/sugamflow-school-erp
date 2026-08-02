package com.sugamflow.school.website.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.website.service.WebsiteResolveService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Platform / Super Admin domain ops for School Website. Uses authenticated tenant context; list-all
 * is for ops roles (gateway already requires JWT).
 */
@RestController
@RequestMapping("/api/website/platform")
public class WebsitePlatformController {

  private final WebsiteResolveService resolveService;

  public WebsitePlatformController(WebsiteResolveService resolveService) {
    this.resolveService = resolveService;
  }

  @GetMapping("/domains")
  public ApiResponse<List<Map<String, Object>>> listDomains() {
    return ApiResponse.ok(resolveService.listAllDomains());
  }

  @PostMapping("/domains")
  public ApiResponse<Map<String, Object>> upsert(@RequestBody Map<String, Object> body) {
    String organizationId = string(body.get("organizationId"));
    if (organizationId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId required");
    }
    boolean primary = Boolean.TRUE.equals(body.get("primary"));
    return ApiResponse.ok(
        resolveService.upsertDomain(
            organizationId,
            string(body.get("host")),
            primary,
            string(body.get("status")),
            string(body.get("sslStatus"))));
  }

  @PutMapping("/domains/ssl")
  public ApiResponse<Map<String, Object>> updateSsl(@RequestBody Map<String, Object> body) {
    String organizationId = string(body.get("organizationId"));
    if (organizationId.isBlank()) {
      organizationId = TenantContext.require().organizationId();
    }
    return ApiResponse.ok(
        resolveService.updateSslStatus(
            organizationId, string(body.get("host")), string(body.get("sslStatus"))));
  }

  private static String string(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
