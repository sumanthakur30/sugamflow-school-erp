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

  public WebsiteResolveService(
      WebsiteDomainRepository domainRepository,
      WebsiteSiteRepository siteRepository,
      ObjectMapper objectMapper,
      @Value("${website.defaults.erp-login-url}") String defaultErpLoginUrl) {
    this.domainRepository = domainRepository;
    this.siteRepository = siteRepository;
    this.objectMapper = objectMapper;
    this.defaultErpLoginUrl = defaultErpLoginUrl;
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
        readMap(site.getThemeJson()),
        readList(site.getHomepageJson()),
        readList(site.getNavigationJson()));
  }

  public List<WebsiteDomain> listDomains(String organizationId) {
    return domainRepository.findByOrganizationIdOrderByPrimaryDescHostAsc(organizationId);
  }

  public Optional<WebsiteSite> findSite(String organizationId) {
    return siteRepository.findByOrganizationId(organizationId);
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
