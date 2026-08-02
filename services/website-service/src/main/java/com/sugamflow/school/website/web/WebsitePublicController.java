package com.sugamflow.school.website.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.website.service.WebsiteAnalyticsService;
import com.sugamflow.school.website.service.WebsiteMarketplaceService;
import com.sugamflow.school.website.service.WebsiteResolveService;
import com.sugamflow.school.website.web.dto.WebsiteResolveResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
  private final WebsiteAnalyticsService analyticsService;
  private final WebsiteMarketplaceService marketplaceService;
  private final RestClient.Builder restClientBuilder;
  private final String cmsBaseUrl;

  public WebsitePublicController(
      WebsiteResolveService resolveService,
      WebsiteAnalyticsService analyticsService,
      WebsiteMarketplaceService marketplaceService,
      RestClient.Builder restClientBuilder,
      @Value("${website.integrations.cms-base-url:http://localhost:8201}") String cmsBaseUrl) {
    this.resolveService = resolveService;
    this.analyticsService = analyticsService;
    this.marketplaceService = marketplaceService;
    this.restClientBuilder = restClientBuilder;
    this.cmsBaseUrl = cmsBaseUrl;
  }

  @GetMapping("/resolve")
  public ApiResponse<WebsiteResolveResponse> resolve(@RequestParam("host") String host) {
    return ApiResponse.ok(resolveService.resolveByHost(host));
  }

  /** Lightweight page-view / funnel tracking (rate-limited at gateway). */
  @PostMapping({"/track", "/events"})
  public ApiResponse<Map<String, Object>> track(
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "User-Agent", required = false) String userAgent) {
    return ApiResponse.ok(analyticsService.track(body, userAgent));
  }

  @GetMapping("/marketplace/templates")
  public ApiResponse<List<Map<String, Object>>> marketplaceTemplates() {
    return ApiResponse.ok(marketplaceService.listTemplates());
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

  /**
   * Crawler-friendly HTML shell with SEO meta (Phase 5 hardening). Edge can serve this for bots
   * while the SPA hydrates for users.
   */
  @GetMapping(value = "/prerender", produces = MediaType.TEXT_HTML_VALUE)
  public String prerender(
      @RequestParam("host") String host,
      @RequestParam(value = "path", defaultValue = "/") String path) {
    WebsiteResolveResponse site = resolveService.resolveByHost(host);
    String title =
        site.seo() != null && site.seo().get("defaultTitle") != null
            ? String.valueOf(site.seo().get("defaultTitle"))
            : site.displayName();
    String description =
        site.seo() != null && site.seo().get("defaultDescription") != null
            ? String.valueOf(site.seo().get("defaultDescription"))
            : site.displayName() + " — official website";
    String safePath = path == null || path.isBlank() ? "/" : path;
    if (!safePath.startsWith("/")) {
      safePath = "/" + safePath;
    }
    String og =
        site.seo() != null && site.seo().get("ogImageUrl") != null
            ? String.valueOf(site.seo().get("ogImageUrl"))
            : "";
    StringBuilder html = new StringBuilder();
    html.append("<!doctype html><html lang=\"en\"><head>");
    html.append("<meta charset=\"utf-8\"/>");
    html.append("<title>").append(escape(title)).append("</title>");
    html.append("<meta name=\"description\" content=\"")
        .append(escape(description))
        .append("\"/>");
    html.append("<meta property=\"og:title\" content=\"").append(escape(title)).append("\"/>");
    html.append("<meta property=\"og:description\" content=\"")
        .append(escape(description))
        .append("\"/>");
    if (!og.isBlank()) {
      html.append("<meta property=\"og:image\" content=\"").append(escape(og)).append("\"/>");
    }
    html.append("<link rel=\"canonical\" href=\"https://")
        .append(escape(site.host()))
        .append(escape(safePath))
        .append("\"/>");
    html.append("<meta name=\"robots\" content=\"index,follow\"/>");
    html.append("</head><body>");
    html.append("<h1>").append(escape(site.displayName())).append("</h1>");
    html.append("<p>").append(escape(description)).append("</p>");
    html.append("<p><a href=\"")
        .append(escape(safePath))
        .append("\">Continue to site</a></p>");
    html.append("<!-- org=")
        .append(escape(site.organizationId()))
        .append(" path=")
        .append(escape(safePath))
        .append(" -->");
    html.append("</body></html>");
    return html.toString();
  }

  private static Map<String, Object> entry(String path, String priority) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("path", path);
    row.put("priority", priority);
    return row;
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
