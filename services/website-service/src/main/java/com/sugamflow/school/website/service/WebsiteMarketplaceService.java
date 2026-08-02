package com.sugamflow.school.website.service;

import com.sugamflow.school.website.persistence.entity.WebsiteSite;
import com.sugamflow.school.website.persistence.entity.WebsiteTemplate;
import com.sugamflow.school.website.persistence.repo.WebsiteSiteRepository;
import com.sugamflow.school.website.persistence.repo.WebsiteTemplateRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebsiteMarketplaceService {

  private final WebsiteTemplateRepository templateRepository;
  private final WebsiteSiteRepository siteRepository;

  public WebsiteMarketplaceService(
      WebsiteTemplateRepository templateRepository, WebsiteSiteRepository siteRepository) {
    this.templateRepository = templateRepository;
    this.siteRepository = siteRepository;
  }

  public List<Map<String, Object>> listTemplates() {
    return templateRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
        .map(this::toRow)
        .toList();
  }

  @Transactional
  public Map<String, Object> applyTemplate(String organizationId, String templateCode, String siteId) {
    WebsiteTemplate template =
        templateRepository
            .findById(templateCode)
            .filter(WebsiteTemplate::isActive)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    WebsiteSite site;
    if (siteId != null && !siteId.isBlank()) {
      site =
          siteRepository
              .findById(java.util.UUID.fromString(siteId.trim()))
              .filter(s -> s.getOrganizationId().equals(organizationId))
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site not found"));
    } else {
      site =
          siteRepository
              .findByOrganizationId(organizationId)
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No website for org"));
    }
    site.setTemplateCode(template.getCode());
    site.setThemeJson(template.getThemeJson());
    site.setHomepageJson(template.getHomepageJson());
    site.setNavigationJson(template.getNavigationJson());
    site.setSeoJson(template.getSeoJson());
    site.setUpdatedAt(Instant.now());
    siteRepository.save(site);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("siteId", site.getId().toString());
    out.put("branchId", site.getBranchId());
    out.put("templateCode", template.getCode());
    out.put("applied", true);
    return out;
  }

  private Map<String, Object> toRow(WebsiteTemplate t) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("code", t.getCode());
    row.put("name", t.getName());
    row.put("description", t.getDescription());
    row.put("previewImageUrl", t.getPreviewImageUrl());
    row.put("sortOrder", t.getSortOrder());
    return row;
  }
}
