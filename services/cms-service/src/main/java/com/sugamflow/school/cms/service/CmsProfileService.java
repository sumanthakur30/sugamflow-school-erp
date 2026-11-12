package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.persistence.entity.CmsProfile;
import com.sugamflow.school.cms.persistence.repo.CmsProfileRepository;
import com.sugamflow.school.cms.web.dto.ProfileResponse;
import com.sugamflow.school.cms.web.dto.ProfileUpsertRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CmsProfileService {

  private static final String PUBLISHED = "PUBLISHED";
  private static final String DRAFT = "DRAFT";

  private final CmsProfileRepository repository;

  public CmsProfileService(CmsProfileRepository repository) {
    this.repository = repository;
  }

  public List<ProfileResponse> listPublished(String organizationId, CmsSiteScope scope) {
    return repository.findByOrganizationIdOrderBySortOrderAscFullNameAsc(require(organizationId)).stream()
        .filter(row -> PUBLISHED.equals(row.getStatus()))
        .filter(row -> scope.matches(row.getSiteId()))
        .sorted(Comparator.comparingInt(CmsProfile::getSortOrder))
        .map(this::toResponse)
        .toList();
  }

  public List<ProfileResponse> listAdmin(String organizationId, CmsSiteScope scope) {
    return repository.findByOrganizationIdOrderBySortOrderAscFullNameAsc(require(organizationId)).stream()
        .filter(row -> scope.matches(row.getSiteId()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public ProfileResponse create(String organizationId, ProfileUpsertRequest request, CmsSiteScope scope) {
    Instant now = Instant.now();
    CmsProfile row = new CmsProfile();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(require(organizationId));
    row.setSiteId(scope.siteId());
    apply(row, request);
    row.setStatus(DRAFT);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    return toResponse(repository.save(row));
  }

  @Transactional
  public ProfileResponse update(String organizationId, UUID id, ProfileUpsertRequest request) {
    CmsProfile row = requireRow(organizationId, id);
    apply(row, request);
    row.setUpdatedAt(Instant.now());
    return toResponse(repository.save(row));
  }

  @Transactional
  public ProfileResponse publish(String organizationId, UUID id) {
    CmsProfile row = requireRow(organizationId, id);
    row.setStatus(PUBLISHED);
    row.setPublishedAt(Instant.now());
    row.setUpdatedAt(Instant.now());
    return toResponse(repository.save(row));
  }

  @Transactional
  public ProfileResponse unpublish(String organizationId, UUID id) {
    CmsProfile row = requireRow(organizationId, id);
    row.setStatus(DRAFT);
    row.setUpdatedAt(Instant.now());
    return toResponse(repository.save(row));
  }

  @Transactional
  public void delete(String organizationId, UUID id) {
    repository.delete(requireRow(organizationId, id));
  }

  private void apply(CmsProfile row, ProfileUpsertRequest request) {
    row.setFullName(request.fullName().trim());
    row.setRole(blank(request.role()));
    row.setPhotoUrl(blank(request.photoUrl()));
    row.setMediaAssetId(request.mediaAssetId());
    row.setSortOrder(request.sortOrder() == null ? 0 : Math.max(0, request.sortOrder()));
  }

  private CmsProfile requireRow(String organizationId, UUID id) {
    return repository
        .findByIdAndOrganizationId(id, require(organizationId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
  }

  private ProfileResponse toResponse(CmsProfile row) {
    return new ProfileResponse(
        row.getId(),
        row.getFullName(),
        row.getRole(),
        row.getPhotoUrl(),
        row.getMediaAssetId(),
        row.getSortOrder(),
        row.getStatus());
  }

  private static String require(String organizationId) {
    if (organizationId == null || organizationId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId is required");
    }
    return organizationId.trim();
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
