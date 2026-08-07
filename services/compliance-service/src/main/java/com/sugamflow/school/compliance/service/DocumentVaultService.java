package com.sugamflow.school.compliance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.config.ComplianceProperties;
import com.sugamflow.school.compliance.dto.ComplianceDocumentRequest;
import com.sugamflow.school.compliance.dto.ComplianceDocumentResponse;
import com.sugamflow.school.compliance.dto.DocumentVaultSummaryResponse;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentEntity;
import com.sugamflow.school.compliance.persistence.repo.ComplianceDocumentRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class DocumentVaultService {
  public static final List<String> RECOMMENDED_DOC_TYPES =
      List.of(
          "AFFILIATION_LETTER",
          "FIRE_NOC",
          "BUILDING_SAFETY",
          "HEALTH_SANITATION",
          "DRINKING_WATER",
          "LAND_OWNERSHIP",
          "TRUST_SOCIETY",
          "RECOGNITION");

  private final ComplianceDocumentRepository repository;
  private final DocumentStorageService storageService;
  private final ConfigEngineClient configEngineClient;
  private final ComplianceProperties properties;

  public DocumentVaultService(
      ComplianceDocumentRepository repository,
      DocumentStorageService storageService,
      ConfigEngineClient configEngineClient,
      ComplianceProperties properties) {
    this.repository = repository;
    this.storageService = storageService;
    this.configEngineClient = configEngineClient;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public List<ComplianceDocumentResponse> list(String docType, String status) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    List<ComplianceDocumentEntity> rows =
        docType == null || docType.isBlank()
            ? repository.findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(
                scope.organizationId())
            : repository.findByOrganizationIdAndDocTypeAndActiveTrueOrderByUpdatedAtDesc(
                scope.organizationId(), docType.trim().toUpperCase(Locale.ROOT));
    if (status != null && !status.isBlank()) {
      String want = status.trim().toUpperCase(Locale.ROOT);
      rows =
          rows.stream()
              .filter(r -> want.equalsIgnoreCase(computeStatus(r.getExpiresOn())))
              .toList();
    }
    return rows.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public DocumentVaultSummaryResponse summary() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    List<ComplianceDocumentEntity> rows =
        repository.findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(
            scope.organizationId());
    long valid =
        rows.stream().filter(r -> "VALID".equalsIgnoreCase(computeStatus(r.getExpiresOn()))).count();
    long expiring =
        rows.stream()
            .filter(r -> "EXPIRING".equalsIgnoreCase(computeStatus(r.getExpiresOn())))
            .count();
    long expired =
        rows.stream()
            .filter(r -> "EXPIRED".equalsIgnoreCase(computeStatus(r.getExpiresOn())))
            .count();
    Set<String> present = new HashSet<>();
    for (ComplianceDocumentEntity row : rows) {
      present.add(row.getDocType());
    }
    List<String> missing =
        RECOMMENDED_DOC_TYPES.stream().filter(t -> !present.contains(t)).toList();
    int warnDays = properties.getDocuments().getExpiryWarnDays();
    LocalDate until = LocalDate.now().plusDays(warnDays);
    List<ComplianceDocumentResponse> expiringSoon =
        rows.stream()
            .filter(
                r -> {
                  String s = computeStatus(r.getExpiresOn());
                  return r.getExpiresOn() != null
                      && !r.getExpiresOn().isAfter(until)
                      && ("EXPIRING".equalsIgnoreCase(s) || "EXPIRED".equalsIgnoreCase(s));
                })
            .limit(20)
            .map(this::toResponse)
            .toList();
    return new DocumentVaultSummaryResponse(
        rows.size(), valid, expiring, expired, missing, expiringSoon);
  }

  @Transactional(readOnly = true)
  public List<ComplianceDocumentResponse> expiring(Integer withinDays) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    int days =
        withinDays == null || withinDays < 1
            ? properties.getDocuments().getExpiryWarnDays()
            : Math.min(withinDays, 365);
    LocalDate until = LocalDate.now().plusDays(days);
    List<ComplianceDocumentEntity> rows =
        repository
            .findByOrganizationIdAndActiveTrueAndExpiresOnNotNullAndExpiresOnLessThanEqualOrderByExpiresOnAsc(
                scope.organizationId(), until);
    return rows.stream().map(this::toResponse).toList();
  }

  @Transactional
  public ComplianceDocumentResponse create(ComplianceDocumentRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = new ComplianceDocumentEntity();
    entity.setOrganizationId(scope.organizationId());
    apply(entity, request, scope);
    entity.setStatus(computeStatus(entity.getExpiresOn()));
    return toResponse(repository.save(entity));
  }

  @Transactional
  public ComplianceDocumentResponse update(Long id, ComplianceDocumentRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    apply(entity, request, scope);
    entity.setStatus(computeStatus(entity.getExpiresOn()));
    return toResponse(repository.save(entity));
  }

  @Transactional
  public void delete(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    entity.setActive(false);
    repository.save(entity);
  }

  @Transactional
  public ComplianceDocumentResponse upload(Long id, MultipartFile file) throws IOException {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    String previous = entity.getStoragePath();
    DocumentStorageService.StoredFile stored =
        storageService.store(scope.organizationId(), entity.getId(), file);
    entity.setFileName(stored.fileName());
    entity.setStoragePath(stored.storagePath());
    entity.setContentType(stored.contentType());
    entity.setFileSize(stored.fileSize());
    entity.setStatus(computeStatus(entity.getExpiresOn()));
    ComplianceDocumentEntity saved = repository.save(entity);
    if (previous != null && !previous.equals(stored.storagePath())) {
      storageService.deleteQuietly(previous);
    }
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public FilePayload download(Long id) throws IOException {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    Path path = storageService.resolve(entity.getStoragePath());
    byte[] bytes = Files.readAllBytes(path);
    String contentType =
        entity.getContentType() == null || entity.getContentType().isBlank()
            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
            : entity.getContentType();
    String fileName =
        entity.getFileName() == null || entity.getFileName().isBlank()
            ? "document-" + id
            : entity.getFileName();
    return new FilePayload(fileName, contentType, new ByteArrayResource(bytes));
  }

  public long countExpiringOrExpired(String organizationId) {
    List<ComplianceDocumentEntity> rows =
        repository.findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(organizationId);
    return rows.stream()
        .map(r -> computeStatus(r.getExpiresOn()))
        .filter(s -> "EXPIRING".equalsIgnoreCase(s) || "EXPIRED".equalsIgnoreCase(s))
        .count();
  }

  public long countMissingRecommended(String organizationId) {
    List<ComplianceDocumentEntity> rows =
        repository.findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(organizationId);
    Set<String> present = new HashSet<>();
    for (ComplianceDocumentEntity row : rows) {
      present.add(row.getDocType());
    }
    return RECOMMENDED_DOC_TYPES.stream().filter(t -> !present.contains(t)).count();
  }

  private void apply(
      ComplianceDocumentEntity entity, ComplianceDocumentRequest request, TenantScope scope) {
    entity.setDocType(request.docType().trim().toUpperCase(Locale.ROOT));
    entity.setTitle(request.title().trim());
    entity.setReferenceNo(trimToNull(request.referenceNo()));
    entity.setIssuer(trimToNull(request.issuer()));
    entity.setIssuedOn(request.issuedOn());
    entity.setExpiresOn(request.expiresOn());
    entity.setExternalUrl(trimToNull(request.externalUrl()));
    entity.setVersionLabel(trimToNull(request.versionLabel()));
    String branch =
        request.branchId() != null && !request.branchId().isBlank()
            ? request.branchId().trim()
            : scope.branchId();
    entity.setBranchId(trimToNull(branch));
    entity.setNotes(trimToNull(request.notes()));
    entity.setActive(true);
  }

  private String computeStatus(LocalDate expiresOn) {
    if (expiresOn == null) {
      return "VALID";
    }
    LocalDate today = LocalDate.now();
    if (expiresOn.isBefore(today)) {
      return "EXPIRED";
    }
    int warnDays = properties.getDocuments().getExpiryWarnDays();
    if (!expiresOn.isAfter(today.plusDays(warnDays))) {
      return "EXPIRING";
    }
    return "VALID";
  }

  private ComplianceDocumentEntity require(Long id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .filter(ComplianceDocumentEntity::isActive)
        .orElseThrow(
            () ->
                new ComplianceException(
                    "NOT_FOUND", "Compliance document not found", HttpStatus.NOT_FOUND));
  }

  private ComplianceDocumentResponse toResponse(ComplianceDocumentEntity e) {
    return new ComplianceDocumentResponse(
        e.getId(),
        e.getOrganizationId(),
        e.getBranchId(),
        e.getDocType(),
        e.getTitle(),
        e.getReferenceNo(),
        e.getIssuer(),
        e.getIssuedOn(),
        e.getExpiresOn(),
        computeStatus(e.getExpiresOn()),
        e.getExternalUrl(),
        e.getFileName(),
        e.getContentType(),
        e.getFileSize(),
        e.getStoragePath() != null && !e.getStoragePath().isBlank(),
        e.getVersionLabel(),
        e.getNotes(),
        e.isActive(),
        e.getUpdatedAt());
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  public record FilePayload(String fileName, String contentType, Resource resource) {}
}
