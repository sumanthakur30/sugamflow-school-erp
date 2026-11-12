package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.persistence.entity.CmsDocument;
import com.sugamflow.school.cms.persistence.repo.CmsDocumentRepository;
import com.sugamflow.school.cms.web.dto.DocumentResponse;
import com.sugamflow.school.cms.web.dto.DocumentUpsertRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CmsDocumentService {

  private static final String PUBLISHED = "PUBLISHED";
  private static final String DRAFT = "DRAFT";

  private final CmsDocumentRepository repository;
  private final CmsMediaService mediaService;

  public CmsDocumentService(CmsDocumentRepository repository, CmsMediaService mediaService) {
    this.repository = repository;
    this.mediaService = mediaService;
  }

  public List<DocumentResponse> listPublished(String organizationId, CmsSiteScope scope, int limit) {
    Instant now = Instant.now();
    int cap = limit <= 0 ? 50 : Math.min(limit, 100);
    return repository
        .findByOrganizationIdAndStatusOrderByPublishedAtDesc(require(organizationId), PUBLISHED)
        .stream()
        .filter(d -> scope.matches(d.getSiteId()))
        .filter(d -> d.getExpiresAt() == null || !d.getExpiresAt().isBefore(now))
        .sorted(
            java.util.Comparator.comparingInt(CmsDocument::getSortOrder)
                .thenComparing(CmsDocument::getTitle, java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
        .limit(cap)
        .map(this::toResponse)
        .toList();
  }

  public List<DocumentResponse> listAdmin(String organizationId, CmsSiteScope scope) {
    return repository.findByOrganizationIdOrderByUpdatedAtDesc(require(organizationId)).stream()
        .filter(d -> scope.matches(d.getSiteId()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public DocumentResponse create(String organizationId, DocumentUpsertRequest request, CmsSiteScope scope) {
    Instant now = Instant.now();
    CmsDocument doc = new CmsDocument();
    doc.setId(UUID.randomUUID());
    doc.setOrganizationId(require(organizationId));
    doc.setSiteId(scope.siteId());
    apply(doc, request);
    doc.setStatus(DRAFT);
    doc.setCreatedAt(now);
    doc.setUpdatedAt(now);
    return toResponse(repository.save(doc));
  }

  @Transactional
  public DocumentResponse update(String organizationId, UUID id, DocumentUpsertRequest request) {
    CmsDocument doc = requireDoc(organizationId, id);
    apply(doc, request);
    doc.setUpdatedAt(Instant.now());
    return toResponse(repository.save(doc));
  }

  @Transactional
  public DocumentResponse publish(String organizationId, UUID id) {
    CmsDocument doc = requireDoc(organizationId, id);
    doc.setStatus(PUBLISHED);
    doc.setPublishedAt(Instant.now());
    doc.setUpdatedAt(Instant.now());
    return toResponse(repository.save(doc));
  }

  @Transactional
  public DocumentResponse unpublish(String organizationId, UUID id) {
    CmsDocument doc = requireDoc(organizationId, id);
    doc.setStatus(DRAFT);
    doc.setUpdatedAt(Instant.now());
    return toResponse(repository.save(doc));
  }

  @Transactional
  public DocumentResponse replaceFile(String organizationId, UUID id, org.springframework.web.multipart.MultipartFile file) {
    CmsDocument doc = requireDoc(organizationId, id);
    UploadRules.Prepared prepared = UploadRules.prepareDocument(file);
    var saved = mediaService.savePrepared(organizationId, doc.getMediaAssetId(), prepared);
    doc.setMediaAssetId(saved.id());
    doc.setFileUrl(saved.url());
    doc.setFileName(saved.fileName());
    doc.setContentType(saved.contentType());
    doc.setByteSize(saved.byteSize());
    doc.setUpdatedAt(Instant.now());
    return toResponse(repository.save(doc));
  }

  private CmsDocument requireDoc(String organizationId, UUID id) {
    return repository
        .findByIdAndOrganizationId(id, require(organizationId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
  }

  private void apply(CmsDocument doc, DocumentUpsertRequest request) {
    doc.setTitle(request.title().trim());
    doc.setCategory(blank(request.category(), "GENERAL"));
    doc.setSummary(blankToNull(request.summary()));
    doc.setFileUrl(request.fileUrl().trim());
    doc.setFileName(blankToNull(request.fileName()));
    doc.setAudience(blank(request.audience(), "PUBLIC"));
    doc.setExpiresAt(request.expiresAt());
    doc.setAcademicYear(blankToNull(request.academicYear()));
    doc.setSortOrder(request.sortOrder() == null ? 0 : Math.max(0, request.sortOrder()));
    doc.setContentType(blankToNull(request.contentType()));
    doc.setByteSize(request.byteSize());
    doc.setMediaAssetId(request.mediaAssetId());
  }

  private DocumentResponse toResponse(CmsDocument d) {
    return new DocumentResponse(
        d.getId(),
        d.getTitle(),
        d.getCategory(),
        d.getSummary(),
        d.getFileUrl(),
        d.getFileName(),
        d.getStatus(),
        d.getAudience(),
        d.getPublishedAt(),
        d.getExpiresAt(),
        d.getAcademicYear(),
        d.getSortOrder(),
        d.getContentType(),
        d.getByteSize(),
        d.getMediaAssetId());
  }

  private static String require(String organizationId) {
    if (organizationId == null || organizationId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId is required");
    }
    return organizationId.trim();
  }

  private static String blank(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim().toUpperCase(java.util.Locale.ROOT);
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
