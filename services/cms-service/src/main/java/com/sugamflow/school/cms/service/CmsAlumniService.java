package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.integration.SubscriptionEntitlementsClient;
import com.sugamflow.school.cms.persistence.entity.CmsAlumniProfile;
import com.sugamflow.school.cms.persistence.repo.CmsAlumniProfileRepository;
import com.sugamflow.school.cms.web.dto.AlumniResponse;
import com.sugamflow.school.cms.web.dto.AlumniUpsertRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CmsAlumniService {

  private static final String FEATURE = "FEATURE_WEBSITE_ALUMNI";
  private static final String PUBLISHED = "PUBLISHED";
  private static final String DRAFT = "DRAFT";

  private final CmsAlumniProfileRepository repository;
  private final SubscriptionEntitlementsClient entitlementsClient;

  public CmsAlumniService(
      CmsAlumniProfileRepository repository, SubscriptionEntitlementsClient entitlementsClient) {
    this.repository = repository;
    this.entitlementsClient = entitlementsClient;
  }

  public List<AlumniResponse> listPublished(String organizationId) {
    return repository
        .findByOrganizationIdAndStatusOrderByBatchYearDescFullNameAsc(requireOrg(organizationId), PUBLISHED)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  public AlumniResponse getPublished(String organizationId, String slug) {
    return repository
        .findByOrganizationIdAndSlugAndStatus(requireOrg(organizationId), normalizeSlug(slug), PUBLISHED)
        .map(this::toResponse)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumni not found"));
  }

  public List<AlumniResponse> listAdmin(String organizationId) {
    requireFeature(organizationId);
    return repository.findByOrganizationIdOrderByUpdatedAtDesc(organizationId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public AlumniResponse create(String organizationId, AlumniUpsertRequest request) {
    requireFeature(organizationId);
    String slug = normalizeSlug(request.slug());
    repository
        .findByOrganizationIdAndSlug(organizationId, slug)
        .ifPresent(
            a -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already used");
            });
    Instant now = Instant.now();
    CmsAlumniProfile row = new CmsAlumniProfile();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(organizationId);
    row.setSlug(slug);
    apply(row, request);
    row.setStatus(DRAFT);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    return toResponse(repository.save(row));
  }

  @Transactional
  public AlumniResponse update(String organizationId, UUID id, AlumniUpsertRequest request) {
    requireFeature(organizationId);
    CmsAlumniProfile row =
        repository
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumni not found"));
    row.setSlug(normalizeSlug(request.slug()));
    apply(row, request);
    row.setUpdatedAt(Instant.now());
    return toResponse(repository.save(row));
  }

  @Transactional
  public AlumniResponse publish(String organizationId, UUID id) {
    requireFeature(organizationId);
    CmsAlumniProfile row =
        repository
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumni not found"));
    row.setStatus(PUBLISHED);
    row.setPublishedAt(Instant.now());
    row.setUpdatedAt(Instant.now());
    return toResponse(repository.save(row));
  }

  private void requireFeature(String organizationId) {
    if (!entitlementsClient.isFeatureEnabled(organizationId, FEATURE)) {
      throw new ResponseStatusException(
          HttpStatus.PAYMENT_REQUIRED, "FEATURE_WEBSITE_ALUMNI is not enabled");
    }
  }

  private void apply(CmsAlumniProfile row, AlumniUpsertRequest request) {
    row.setFullName(request.fullName().trim());
    row.setBatchYear(request.batchYear());
    row.setHeadline(blankToNull(request.headline()));
    row.setBioHtml(request.bioHtml() == null ? "" : request.bioHtml());
    row.setPhotoUrl(blankToNull(request.photoUrl()));
    row.setBranchId(
        request.branchId() == null || request.branchId().isBlank() ? "main" : request.branchId().trim());
  }

  private AlumniResponse toResponse(CmsAlumniProfile a) {
    return new AlumniResponse(
        a.getId(),
        a.getSlug(),
        a.getFullName(),
        a.getBatchYear(),
        a.getHeadline(),
        a.getBioHtml(),
        a.getPhotoUrl(),
        a.getStatus(),
        a.getBranchId(),
        a.getPublishedAt());
  }

  private static String requireOrg(String organizationId) {
    if (organizationId == null || organizationId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId is required");
    }
    return organizationId.trim();
  }

  private static String normalizeSlug(String slug) {
    if (slug == null || slug.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug is required");
    }
    return slug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]+", "-");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
