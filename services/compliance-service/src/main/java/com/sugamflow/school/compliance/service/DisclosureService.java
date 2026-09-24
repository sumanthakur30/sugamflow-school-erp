package com.sugamflow.school.compliance.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.dto.DisclosurePackageResponse;
import com.sugamflow.school.compliance.dto.DisclosurePublishRequest;
import com.sugamflow.school.compliance.integration.CmsClient;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.persistence.entity.DisclosureBindingEntity;
import com.sugamflow.school.compliance.persistence.repo.DisclosureBindingRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class DisclosureService {
  public static final String FEATURE_BOARD_DISCLOSURE = "FEATURE_BOARD_DISCLOSURE";
  public static final String SECTION_MAIN = "MAIN";

  private final DisclosureBindingRepository bindingRepository;
  private final DisclosurePackageBuilder packageBuilder;
  private final CmsClient cmsClient;
  private final ConfigEngineClient configEngineClient;

  public DisclosureService(
      DisclosureBindingRepository bindingRepository,
      DisclosurePackageBuilder packageBuilder,
      CmsClient cmsClient,
      ConfigEngineClient configEngineClient) {
    this.bindingRepository = bindingRepository;
    this.packageBuilder = packageBuilder;
    this.cmsClient = cmsClient;
    this.configEngineClient = configEngineClient;
  }

  @Transactional(readOnly = true)
  public DisclosurePackageResponse preview() {
    TenantScope scope = TenantContext.require();
    requireCompliance(scope);
    DisclosureBindingEntity binding = bindingRepository
        .findByOrganizationIdAndSectionKey(scope.organizationId(), SECTION_MAIN)
        .orElse(null);
    DisclosurePackageBuilder.BuiltPackage pack = packageBuilder.build(scope);
    String slug =
        binding != null && binding.getCmsSlug() != null
            ? binding.getCmsSlug()
            : DisclosurePackageBuilder.DEFAULT_SLUG;
    String title =
        binding != null && binding.getTitle() != null
            ? binding.getTitle()
            : DisclosurePackageBuilder.DEFAULT_TITLE;
    return toResponse(scope.organizationId(), slug, title, pack, binding);
  }

  @Transactional
  public DisclosurePackageResponse publish(DisclosurePublishRequest request) {
    TenantScope scope = TenantContext.require();
    requireCompliance(scope);
    requireDisclosure(scope);

    DisclosureBindingEntity binding =
        bindingRepository
            .findByOrganizationIdAndSectionKey(scope.organizationId(), SECTION_MAIN)
            .orElseGet(
                () -> {
                  DisclosureBindingEntity created = new DisclosureBindingEntity();
                  created.setOrganizationId(scope.organizationId());
                  created.setSectionKey(SECTION_MAIN);
                  return created;
                });

    if (request != null && request.slug() != null && !request.slug().isBlank()) {
      binding.setCmsSlug(normalizeSlug(request.slug()));
    } else if (binding.getCmsSlug() == null || binding.getCmsSlug().isBlank()) {
      binding.setCmsSlug(DisclosurePackageBuilder.DEFAULT_SLUG);
    }
    if (request != null && request.title() != null && !request.title().isBlank()) {
      binding.setTitle(request.title().trim());
    } else if (binding.getTitle() == null || binding.getTitle().isBlank()) {
      binding.setTitle(DisclosurePackageBuilder.DEFAULT_TITLE);
    }

    DisclosurePackageBuilder.BuiltPackage pack = packageBuilder.build(scope);
    boolean publishNow = request == null || request.publishNow() == null || Boolean.TRUE.equals(request.publishNow());

    try {
      if (publishNow) {
        Map<String, Object> published =
            cmsClient.upsertAndPublish(
                scope,
                binding.getCmsSlug(),
                binding.getTitle(),
                pack.summary(),
                pack.bodyHtml(),
                binding.getTitle() + " | " + pack.schoolName(),
                pack.summary());
        binding.setCmsPageId(str(published.get("id")));
        binding.setLastPublishStatus("PUBLISHED");
        binding.setLastPublishMessage("Published to CMS slug " + binding.getCmsSlug());
        binding.setLastPublishedAt(Instant.now());
        binding.setPublicUrlHint("/" + binding.getCmsSlug());
      } else {
        Map<String, Object> existing = cmsClient.findPageBySlug(scope, binding.getCmsSlug());
        java.util.LinkedHashMap<String, Object> upsert = new java.util.LinkedHashMap<>();
        upsert.put("slug", binding.getCmsSlug());
        upsert.put("title", binding.getTitle());
        upsert.put("summary", pack.summary());
        upsert.put("bodyHtml", pack.bodyHtml());
        upsert.put("seoTitle", binding.getTitle() + " | " + pack.schoolName());
        upsert.put("seoDescription", pack.summary());
        Map<String, Object> payloadSaved =
            existing == null || existing.get("id") == null
                ? cmsClient.createPage(scope, upsert)
                : cmsClient.updatePage(scope, str(existing.get("id")), upsert);
        binding.setCmsPageId(str(payloadSaved.get("id")));
        binding.setLastPublishStatus("DRAFT");
        binding.setLastPublishMessage("Saved CMS draft for slug " + binding.getCmsSlug());
        binding.setPublicUrlHint("/" + binding.getCmsSlug());
      }
      binding.setPublishedHtml(pack.bodyHtml());
      binding.setPublishedSnapshot(pack.snapshot());
      binding = bindingRepository.save(binding);
      return toResponse(scope.organizationId(), binding.getCmsSlug(), binding.getTitle(), pack, binding);
    } catch (ComplianceException ex) {
      binding.setLastPublishStatus("FAILED");
      binding.setLastPublishMessage(ex.getMessage());
      bindingRepository.save(binding);
      throw ex;
    } catch (Exception ex) {
      binding.setLastPublishStatus("FAILED");
      binding.setLastPublishMessage(ex.getMessage());
      bindingRepository.save(binding);
      throw new ComplianceException(
          "PUBLISH_FAILED", "Disclosure publish failed: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }
  }

  @Transactional(readOnly = true)
  public DisclosurePackageResponse publicDisclosure(String organizationId) {
    if (organizationId == null || organizationId.isBlank()) {
      throw new ComplianceException("BAD_REQUEST", "organizationId is required", HttpStatus.BAD_REQUEST);
    }
    String org = organizationId.trim();
    DisclosureBindingEntity binding =
        bindingRepository
            .findByOrganizationIdAndSectionKey(org, SECTION_MAIN)
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND",
                        "No published disclosure for organization " + org,
                        HttpStatus.NOT_FOUND));
    if (binding.getPublishedSnapshot() == null || binding.getPublishedSnapshot().isEmpty()) {
      throw new ComplianceException(
          "NOT_FOUND", "Disclosure has not been published yet", HttpStatus.NOT_FOUND);
    }
    if (!"PUBLISHED".equalsIgnoreCase(binding.getLastPublishStatus())) {
      throw new ComplianceException(
          "NOT_FOUND", "Disclosure is not published yet", HttpStatus.NOT_FOUND);
    }
    // Public JSON prefers last published snapshot (no live master re-query).
    DisclosurePackageBuilder.BuiltPackage pack =
        new DisclosurePackageBuilder.BuiltPackage(
            str(binding.getPublishedSnapshot().get("schoolName")),
            "Published mandatory disclosure package",
            binding.getPublishedHtml() == null ? "" : binding.getPublishedHtml(),
            binding.getPublishedSnapshot(),
            java.util.List.of());
    return toResponse(org, binding.getCmsSlug(), binding.getTitle(), pack, binding);
  }

  private DisclosurePackageResponse toResponse(
      String org,
      String slug,
      String title,
      DisclosurePackageBuilder.BuiltPackage pack,
      DisclosureBindingEntity binding) {
    return new DisclosurePackageResponse(
        org,
        slug,
        title,
        pack.summary(),
        pack.bodyHtml(),
        pack.snapshot(),
        pack.warnings(),
        Instant.now(),
        binding != null ? binding.getLastPublishedAt() : null,
        binding != null ? binding.getLastPublishStatus() : null,
        binding != null ? binding.getCmsPageId() : null,
        binding != null ? binding.getPublicUrlHint() : null);
  }

  private void requireCompliance(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private void requireDisclosure(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, FEATURE_BOARD_DISCLOSURE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_BOARD_DISCLOSURE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private static String normalizeSlug(String slug) {
    return slug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }
}
