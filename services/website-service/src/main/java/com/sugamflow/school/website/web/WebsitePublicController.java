package com.sugamflow.school.website.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.website.service.WebsiteResolveService;
import com.sugamflow.school.website.web.dto.WebsiteResolveResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated public APIs for the School Website Platform. Tenant is resolved from {@code
 * host}, never trusted from client-supplied organization id alone.
 */
@RestController
@RequestMapping("/api/website/public")
public class WebsitePublicController {

  private final WebsiteResolveService resolveService;

  public WebsitePublicController(WebsiteResolveService resolveService) {
    this.resolveService = resolveService;
  }

  @GetMapping("/resolve")
  public ApiResponse<WebsiteResolveResponse> resolve(@RequestParam("host") String host) {
    return ApiResponse.ok(resolveService.resolveByHost(host));
  }
}
