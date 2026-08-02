package com.sugamflow.school.website.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.website.persistence.entity.WebsiteDomain;
import com.sugamflow.school.website.persistence.entity.WebsiteSite;
import com.sugamflow.school.website.persistence.repo.WebsiteDomainRepository;
import com.sugamflow.school.website.persistence.repo.WebsiteSiteRepository;
import com.sugamflow.school.website.web.dto.WebsiteResolveResponse;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebsiteResolveService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};

  private final WebsiteDomainRepository domainRepository;
  private final WebsiteSiteRepository siteRepository;
  private final ObjectMapper objectMapper;
  private final String defaultErpLoginUrl;
  private final String cdnBaseUrl;

  public WebsiteResolveService(
      WebsiteDomainRepository domainRepository,
      WebsiteSiteRepository siteRepository,
      ObjectMapper objectMapper,
      @Value("${website.defaults.erp-login-url}") String defaultErpLoginUrl,
      @Value("${website.cdn.base-url:}") String cdnBaseUrl) {
    this.domainRepository = domainRepository;
    this.siteRepository = siteRepository;
    this.objectMapper = objectMapper;
    this.defaultErpLoginUrl = defaultErpLoginUrl;
    this.cdnBaseUrl = cdnBaseUrl == null ? "" : cdnBaseUrl.trim().replaceAll("/$", "");
  }

  public WebsiteResolveResponse resolveByHost(String rawHost) {
    String host = normalizeHost(rawHost);
    if (host.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "host is required");
    }

    WebsiteDomain domain =
        domainRepository
            .findByHostIgnoreCaseAndStatus(host, "ACTIVE")
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active website for host: " + host));

    WebsiteSite site =
        siteRepository
            .findByOrganizationId(domain.getOrganizationId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Website site missing for organization: " + domain.getOrganizationId()));

    if ("SUSPENDED".equalsIgnoreCase(site.getStatus())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Website is suspended");
    }

    String erpLogin =
        Optional.ofNullable(site.getErpLoginUrl())
            .filter(u -> !u.isBlank())
            .orElse(defaultErpLoginUrl);

    return new WebsiteResolveResponse(
        site.getOrganizationId(),
        domain.getHost(),
        site.getStatus(),
        site.getTemplateCode(),
        site.getDisplayName(),
        erpLogin,
        cdnBaseUrl,
        readMap(site.getThemeJson()),
        readList(site.getHomepageJson()),
        readList(site.getNavigationJson()),
        readMap(site.getSeoJson()));
  }

  public List<WebsiteDomain> listDomains(String organizationId) {
    return domainRepository.findByOrganizationIdOrderByPrimaryDescHostAsc(organizationId);
  }

  public Optional<WebsiteSite> findSite(String organizationId) {
    return siteRepository.findByOrganizationId(organizationId);
  }

  @org.springframework.transaction.annotation.Transactional
  public List<Map<String, Object>> updateHomepage(
      String organizationId, List<Map<String, Object>> sections) {
    WebsiteSite site = requireSite(organizationId);
    List<Map<String, Object>> normalized = normalizeSections(sections);
    try {
      site.setHomepageJson(objectMapper.writeValueAsString(normalized));
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid homepage JSON");
    }
    site.setUpdatedAt(java.time.Instant.now());
    siteRepository.save(site);
    return normalized;
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> updateTheme(String organizationId, Map<String, Object> theme) {
    WebsiteSite site = requireSite(organizationId);
    Map<String, Object> safe = theme == null ? Collections.emptyMap() : theme;
    try {
      site.setThemeJson(objectMapper.writeValueAsString(safe));
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid theme JSON");
    }
    site.setUpdatedAt(java.time.Instant.now());
    siteRepository.save(site);
    return safe;
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> updateSeo(String organizationId, Map<String, Object> seo) {
    WebsiteSite site = requireSite(organizationId);
    Map<String, Object> safe = seo == null ? Collections.emptyMap() : seo;
    try {
      site.setSeoJson(objectMapper.writeValueAsString(safe));
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SEO JSON");
    }
    site.setUpdatedAt(java.time.Instant.now());
    siteRepository.save(site);
    return safe;
  }

  private WebsiteSite requireSite(String organizationId) {
    return siteRepository
        .findByOrganizationId(organizationId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No website configured for this organization"));
  }

  private List<Map<String, Object>> normalizeSections(List<Map<String, Object>> sections) {
    if (sections == null) {
      return Collections.emptyList();
    }
    java.util.ArrayList<Map<String, Object>> copy = new java.util.ArrayList<>();
    int i = 0;
    for (Map<String, Object> section : sections) {
      if (section == null || section.get("type") == null) {
        continue;
      }
      java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<>(section);
      Object enabled = row.get("enabled");
      if (enabled == null) {
        row.put("enabled", true);
      }
      if (row.get("order") == null) {
        row.put("order", (i + 1) * 10);
      }
      copy.add(row);
      i++;
    }
    copy.sort(
        (a, b) ->
            Integer.compare(
                toInt(a.get("order"), 0),
                toInt(b.get("order"), 0)));
    return copy;
  }

  private static int toInt(Object value, int fallback) {
    if (value instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (Exception ex) {
      return fallback;
    }
  }

  /** Strip scheme, path, port; lowercase; trim trailing dot. */
  static String normalizeHost(String rawHost) {
    if (rawHost == null) {
      return "";
    }
    String host = rawHost.trim().toLowerCase(Locale.ROOT);
    if (host.startsWith("http://")) {
      host = host.substring("http://".length());
    } else if (host.startsWith("https://")) {
      host = host.substring("https://".length());
    }
    int slash = host.indexOf('/');
    if (slash >= 0) {
      host = host.substring(0, slash);
    }
    int colon = host.indexOf(':');
    if (colon >= 0) {
      host = host.substring(0, colon);
    }
    while (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    return host;
  }

  private Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception ex) {
      return Collections.emptyMap();
    }
  }

  private List<Map<String, Object>> readList(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(json, LIST_TYPE);
    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }
}
