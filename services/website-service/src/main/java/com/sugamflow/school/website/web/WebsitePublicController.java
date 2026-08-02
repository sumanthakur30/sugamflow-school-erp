package com.sugamflow.school.website.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.website.service.WebsiteResolveService;
import com.sugamflow.school.website.web.dto.WebsiteResolveResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Unauthenticated public APIs for the School Website Platform. Tenant is resolved from {@code
 * host}, never trusted from client-supplied organization id alone.
 */
@RestController
@RequestMapping("/api/website/public")
public class WebsitePublicController {

  private final WebsiteResolveService resolveService;
  private final RestClient.Builder restClientBuilder;
  private final String cmsBaseUrl;

  public WebsitePublicController(
      WebsiteResolveService resolveService,
      RestClient.Builder restClientBuilder,
      @Value("${website.integrations.cms-base-url:http://localhost:8201}") String cmsBaseUrl) {
    this.resolveService = resolveService;
    this.restClientBuilder = restClientBuilder;
    this.cmsBaseUrl = cmsBaseUrl;
  }

  @GetMapping("/resolve")
  public ApiResponse<WebsiteResolveResponse> resolve(@RequestParam("host") String host) {
    return ApiResponse.ok(resolveService.resolveByHost(host));
  }

  /** Machine-readable sitemap entries for the public Angular app / CDN. */
  @GetMapping("/sitemap")
  public ApiResponse<Map<String, Object>> sitemap(@RequestParam("host") String host) {
    WebsiteResolveResponse site = resolveService.resolveByHost(host);
    List<Map<String, Object>> urls = new ArrayList<>();
    urls.add(entry("/", "1.0"));
    urls.add(entry("/admission", "0.9"));
    urls.add(entry("/admission/apply", "0.8"));
    urls.add(entry("/news", "0.7"));
    urls.add(entry("/events", "0.7"));
    urls.add(entry("/gallery", "0.6"));

    for (Map<String, Object> nav : site.navigation()) {
      Object path = nav.get("path");
      if (path == null) {
        continue;
      }
      String p = String.valueOf(path);
      if (p.startsWith("/") && !"/".equals(p)) {
        urls.add(entry(p, "0.6"));
      }
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          restClientBuilder
              .build()
              .get()
              .uri(
                  cmsBaseUrl + "/api/cms/public/pages?organizationId={org}",
                  site.organizationId())
              .header("X-Gateway-Verified", "true")
              .header("X-Tenant-Id", site.organizationId())
              .retrieve()
              .body(Map.class);
      Object data = response != null ? response.get("data") : null;
      if (data instanceof List<?> list) {
        for (Object row : list) {
          if (row instanceof Map<?, ?> page) {
            Object slug = page.get("slug");
            if (slug != null && !String.valueOf(slug).isBlank()) {
              urls.add(entry("/" + slug, "0.5"));
            }
          }
        }
      }
    } catch (Exception ignored) {
      // CMS optional for sitemap shell
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("host", site.host());
    payload.put("organizationId", site.organizationId());
    payload.put("seo", site.seo());
    payload.put("urls", urls);
    return ApiResponse.ok(payload);
  }

  @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
  public String robots(@RequestParam("host") String host) {
    WebsiteResolveResponse site = resolveService.resolveByHost(host);
    return "User-agent: *\nAllow: /\nSitemap: https://"
        + site.host()
        + "/api/website/public/sitemap?host="
        + site.host()
        + "\n";
  }

  private static Map<String, Object> entry(String path, String priority) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("path", path);
    row.put("priority", priority);
    return row;
  }
}
