package com.sugamflow.school.compliance.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.config.ComplianceProperties;
import com.sugamflow.school.compliance.web.ComplianceException;

@Component
public class CmsClient {
  private static final Logger log = LoggerFactory.getLogger(CmsClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final ComplianceProperties properties;

  public CmsClient(RestClient.Builder restClientBuilder, ComplianceProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public List<Map<String, Object>> listPages(TenantScope scope) {
    Object data = getData(base() + "/api/cms/admin/pages", scope);
    return asMapList(data);
  }

  public Map<String, Object> createPage(TenantScope scope, Map<String, Object> body) {
    return post(base() + "/api/cms/admin/pages", scope, body);
  }

  public Map<String, Object> updatePage(TenantScope scope, String pageId, Map<String, Object> body) {
    return put(base() + "/api/cms/admin/pages/" + pageId, scope, body);
  }

  public Map<String, Object> publishPage(TenantScope scope, String pageId) {
    return post(base() + "/api/cms/admin/pages/" + pageId + "/publish", scope, Map.of());
  }

  public Map<String, Object> findPageBySlug(TenantScope scope, String slug) {
    String want = slug == null ? "" : slug.trim().toLowerCase();
    for (Map<String, Object> page : listPages(scope)) {
      if (want.equalsIgnoreCase(str(page.get("slug")))) {
        return page;
      }
    }
    return null;
  }

  public Map<String, Object> upsertAndPublish(
      TenantScope scope,
      String slug,
      String title,
      String summary,
      String bodyHtml,
      String seoTitle,
      String seoDescription) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("slug", slug);
    payload.put("title", title);
    payload.put("summary", summary);
    payload.put("bodyHtml", bodyHtml);
    payload.put("seoTitle", seoTitle);
    payload.put("seoDescription", seoDescription);

    Map<String, Object> existing = findPageBySlug(scope, slug);
    Map<String, Object> saved;
    if (existing == null || existing.get("id") == null) {
      saved = createPage(scope, payload);
    } else {
      saved = updatePage(scope, String.valueOf(existing.get("id")), payload);
    }
    String pageId = str(saved.get("id"));
    if (pageId.isEmpty()) {
      throw new ComplianceException(
          "CMS_ERROR", "CMS page save returned no id", HttpStatus.BAD_GATEWAY);
    }
    // validate UUID-ish without failing on string ids
    try {
      UUID.fromString(pageId);
    } catch (Exception ignored) {
      // keep going — some envs may stringify differently
    }
    return publishPage(scope, pageId);
  }

  private String base() {
    return properties.getIntegrations().getCmsBaseUrl();
  }

  private Object getData(String url, TenantScope scope) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      return envelope == null ? null : envelope.get("data");
    } catch (RestClientResponseException ex) {
      log.warn("CMS GET {} failed: {}", url, ex.getStatusCode());
      throw new ComplianceException(
          "CMS_ERROR", "CMS GET failed: " + ex.getStatusCode(), HttpStatus.BAD_GATEWAY);
    } catch (Exception ex) {
      log.warn("CMS GET {} failed: {}", url, ex.getMessage());
      throw new ComplianceException(
          "CMS_ERROR", "CMS GET failed: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }
  }

  private Map<String, Object> post(String url, TenantScope scope, Map<String, Object> body) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .post()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .body(body == null ? Map.of() : body)
              .retrieve()
              .body(MAP_TYPE);
      return unwrapMap(envelope);
    } catch (RestClientResponseException ex) {
      log.warn("CMS POST {} failed: {} {}", url, ex.getStatusCode(), ex.getResponseBodyAsString());
      throw new ComplianceException(
          "CMS_ERROR",
          "CMS POST failed: " + ex.getStatusCode() + " " + safeBody(ex),
          HttpStatus.BAD_GATEWAY);
    } catch (ComplianceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ComplianceException(
          "CMS_ERROR", "CMS POST failed: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }
  }

  private Map<String, Object> put(String url, TenantScope scope, Map<String, Object> body) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .put()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .body(body == null ? Map.of() : body)
              .retrieve()
              .body(MAP_TYPE);
      return unwrapMap(envelope);
    } catch (RestClientResponseException ex) {
      log.warn("CMS PUT {} failed: {} {}", url, ex.getStatusCode(), ex.getResponseBodyAsString());
      throw new ComplianceException(
          "CMS_ERROR",
          "CMS PUT failed: " + ex.getStatusCode() + " " + safeBody(ex),
          HttpStatus.BAD_GATEWAY);
    } catch (ComplianceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ComplianceException(
          "CMS_ERROR", "CMS PUT failed: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> unwrapMap(Map<String, Object> envelope) {
    if (envelope == null) {
      return Map.of();
    }
    Object data = envelope.get("data");
    if (data instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return envelope;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> asMapList(Object data) {
    if (!(data instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m) {
        out.add((Map<String, Object>) m);
      }
    }
    return out;
  }

  private static String safeBody(RestClientResponseException ex) {
    String body = ex.getResponseBodyAsString();
    if (body == null) {
      return "";
    }
    return body.length() > 240 ? body.substring(0, 240) : body;
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }
}
