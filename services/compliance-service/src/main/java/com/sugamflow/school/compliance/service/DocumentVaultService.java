package com.sugamflow.school.compliance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import com.sugamflow.school.compliance.dto.DocumentAuditResponse;
import com.sugamflow.school.compliance.dto.DocumentCategoryResponse;
import com.sugamflow.school.compliance.dto.DocumentFolderRequest;
import com.sugamflow.school.compliance.dto.DocumentFolderResponse;
import com.sugamflow.school.compliance.dto.DocumentVaultSummaryResponse;
import com.sugamflow.school.compliance.dto.DocumentVersionResponse;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentAuditEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentCategoryEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentFolderEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentVersionEntity;
import com.sugamflow.school.compliance.persistence.repo.ComplianceDocumentAuditRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceDocumentCategoryRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceDocumentFolderRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceDocumentRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceDocumentVersionRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class DocumentVaultService {
  public static final int MAX_FOLDER_DEPTH = 4;
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

  private static final List<String[]> DEFAULT_CATEGORIES =
      List.of(
          new String[] {"SCHOOL_REGISTRATION", "School & Registration", "SCHOOL"},
          new String[] {"BOARD_AFFILIATION", "Board & Affiliation", "BOARD"},
          new String[] {"SAFETY_INFRASTRUCTURE", "Safety & Infrastructure", "SAFETY"},
          new String[] {"STAFF_COMPLIANCE", "Staff Compliance", "STAFF"},
          new String[] {"STUDENT_COMPLIANCE", "Student Compliance", "STUDENT"},
          new String[] {"FINANCE_LEGAL", "Finance & Legal", "FINANCE"},
          new String[] {"TRANSPORT", "Transport", "TRANSPORT"},
          new String[] {"ACADEMIC", "Academic", "ACADEMIC"},
          new String[] {"POLICIES", "Policies", "POLICY"},
          new String[] {"OTHER", "Other", "OTHER"});

  private static final Set<String> APPROVER_ROLES =
      Set.of(
          "SHOP_OWNER",
          "SCHOOL_OWNER",
          "SUPER_ADMIN",
          "ADMIN",
          "PRINCIPAL",
          "COMPLIANCE_ADMIN");

  private final ComplianceDocumentRepository repository;
  private final ComplianceDocumentVersionRepository versionRepository;
  private final ComplianceDocumentAuditRepository auditRepository;
  private final ComplianceDocumentCategoryRepository categoryRepository;
  private final ComplianceDocumentFolderRepository folderRepository;
  private final DocumentStorageService storageService;
  private final ConfigEngineClient configEngineClient;
  private final ComplianceProperties properties;

  public DocumentVaultService(
      ComplianceDocumentRepository repository,
      ComplianceDocumentVersionRepository versionRepository,
      ComplianceDocumentAuditRepository auditRepository,
      ComplianceDocumentCategoryRepository categoryRepository,
      ComplianceDocumentFolderRepository folderRepository,
      DocumentStorageService storageService,
      ConfigEngineClient configEngineClient,
      ComplianceProperties properties) {
    this.repository = repository;
    this.versionRepository = versionRepository;
    this.auditRepository = auditRepository;
    this.categoryRepository = categoryRepository;
    this.folderRepository = folderRepository;
    this.storageService = storageService;
    this.configEngineClient = configEngineClient;
    this.properties = properties;
  }

  @Transactional
  public List<ComplianceDocumentResponse> list(
      String docType, String status, String q, String category, String area) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ensureCategories(scope.organizationId());
    List<ComplianceDocumentEntity> rows;
    if (status != null && "ARCHIVED".equalsIgnoreCase(status.trim())) {
      rows = repository.findByOrganizationIdAndActiveFalseOrderByUpdatedAtDesc(scope.organizationId());
    } else if (docType == null || docType.isBlank()) {
      rows =
          repository.findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(
              scope.organizationId());
    } else {
      rows =
          repository.findByOrganizationIdAndDocTypeAndActiveTrueOrderByUpdatedAtDesc(
              scope.organizationId(), docType.trim().toUpperCase(Locale.ROOT));
    }
    String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    return rows.stream()
        .filter(r -> visibleCampus(r, scope))
        .filter(r -> category == null || category.isBlank() || category.equalsIgnoreCase(r.getCategoryCode()))
        .filter(r -> area == null || area.isBlank() || area.equalsIgnoreCase(r.getComplianceArea()))
        .filter(r -> matchesStatus(r, status))
        .filter(r -> needle.isEmpty() || searchBlob(r).contains(needle))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public DocumentVaultSummaryResponse summary() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ensureCategories(scope.organizationId());
    List<ComplianceDocumentEntity> rows =
        repository
            .findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(scope.organizationId())
            .stream()
            .filter(r -> visibleCampus(r, scope))
            .toList();
    long valid =
        rows.stream().filter(r -> "VALID".equalsIgnoreCase(computeExpiry(r.getExpiresOn()))).count();
    long expiring =
        rows.stream().filter(r -> "EXPIRING".equalsIgnoreCase(computeExpiry(r.getExpiresOn()))).count();
    long expired =
        rows.stream().filter(r -> "EXPIRED".equalsIgnoreCase(computeExpiry(r.getExpiresOn()))).count();
    long approved =
        rows.stream().filter(r -> "APPROVED".equalsIgnoreCase(workflowOf(r)) && !"EXPIRED".equals(computeExpiry(r.getExpiresOn()))).count();
    long pending = rows.stream().filter(r -> "PENDING_REVIEW".equalsIgnoreCase(workflowOf(r))).count();
    long draft = rows.stream().filter(r -> "DRAFT".equalsIgnoreCase(workflowOf(r))).count();
    long storage =
        rows.stream().map(ComplianceDocumentEntity::getFileSize).filter(s -> s != null).mapToLong(Long::longValue).sum();
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
                r ->
                    r.getExpiresOn() != null
                        && !r.getExpiresOn().isAfter(until)
                        && ("EXPIRING".equalsIgnoreCase(computeExpiry(r.getExpiresOn()))
                            || "EXPIRED".equalsIgnoreCase(computeExpiry(r.getExpiresOn()))))
            .limit(20)
            .map(this::toResponse)
            .toList();
    return new DocumentVaultSummaryResponse(
        rows.size(), valid, expiring, expired, missing, expiringSoon, approved, pending, draft, storage);
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
    return repository
        .findByOrganizationIdAndActiveTrueAndExpiresOnNotNullAndExpiresOnLessThanEqualOrderByExpiresOnAsc(
            scope.organizationId(), until)
        .stream()
        .filter(r -> visibleCampus(r, scope))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public List<DocumentCategoryResponse> categories() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ensureCategories(scope.organizationId());
    return categoryRepository
        .findByOrganizationIdAndActiveTrueOrderBySortOrderAscNameAsc(scope.organizationId())
        .stream()
        .map(c -> new DocumentCategoryResponse(c.getId(), c.getCode(), c.getName(), c.getComplianceArea()))
        .toList();
  }

  @Transactional
  public List<DocumentFolderResponse> folders() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return folderRepository.findByOrganizationIdAndActiveTrueOrderByNameAsc(scope.organizationId()).stream()
        .map(this::folderResponse)
        .toList();
  }

  @Transactional
  public DocumentFolderResponse createFolder(DocumentFolderRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentFolderEntity folder = new ComplianceDocumentFolderEntity();
    folder.setOrganizationId(scope.organizationId());
    folder.setName(request.name().trim());
    folder.setDescription(trimToNull(request.description()));
    folder.setVisibility(request.visibility() == null || request.visibility().isBlank() ? "SCHOOL" : request.visibility().trim().toUpperCase(Locale.ROOT));
    folder.setComplianceArea(trimToNull(request.complianceArea()));
    int depth = 1;
    if (request.parentId() != null) {
      ComplianceDocumentFolderEntity parent =
          folderRepository
              .findByIdAndOrganizationId(request.parentId(), scope.organizationId())
              .orElseThrow(
                  () -> new ComplianceException("NOT_FOUND", "Parent folder not found", HttpStatus.NOT_FOUND));
      depth = parent.getDepth() + 1;
      if (depth > MAX_FOLDER_DEPTH) {
        throw new ComplianceException(
            "FOLDER_DEPTH", "Folders can be nested at most " + MAX_FOLDER_DEPTH + " levels", HttpStatus.BAD_REQUEST);
      }
      folder.setParentId(parent.getId());
    }
    folder.setDepth(depth);
    return folderResponse(folderRepository.save(folder));
  }

  @Transactional
  public ComplianceDocumentResponse create(ComplianceDocumentRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = new ComplianceDocumentEntity();
    entity.setOrganizationId(scope.organizationId());
    entity.setPublicCode(nextPublicCode(scope.organizationId()));
    entity.setWorkflowStatus("DRAFT");
    entity.setVersionNo(1);
    entity.setUploadedBy(scope.userId());
    apply(entity, request, scope);
    entity.setStatus(computeExpiry(entity.getExpiresOn()));
    ComplianceDocumentEntity saved = repository.save(entity);
    audit(saved, scope, "UPLOAD", "Created " + saved.getPublicCode());
    return toResponse(saved);
  }

  @Transactional
  public ComplianceDocumentResponse update(Long id, ComplianceDocumentRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    apply(entity, request, scope);
    entity.setUpdatedBy(scope.userId());
    entity.setStatus(computeExpiry(entity.getExpiresOn()));
    ComplianceDocumentEntity saved = repository.save(entity);
    audit(saved, scope, "EDIT_METADATA", "Updated metadata");
    return toResponse(saved);
  }

  @Transactional
  public void delete(Long id) {
    archive(id);
  }

  @Transactional
  public ComplianceDocumentResponse archive(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    entity.setActive(false);
    entity.setWorkflowStatus("ARCHIVED");
    entity.setUpdatedBy(scope.userId());
    ComplianceDocumentEntity saved = repository.save(entity);
    audit(saved, scope, "ARCHIVE", "Archived");
    return toResponse(saved);
  }

  @Transactional
  public ComplianceDocumentResponse restore(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity =
        repository
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(
                () -> new ComplianceException("NOT_FOUND", "Compliance document not found", HttpStatus.NOT_FOUND));
    entity.setActive(true);
    entity.setWorkflowStatus("DRAFT");
    entity.setUpdatedBy(scope.userId());
    ComplianceDocumentEntity saved = repository.save(entity);
    audit(saved, scope, "RESTORE", "Restored to draft");
    return toResponse(saved);
  }

  @Transactional
  public ComplianceDocumentResponse submit(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    entity.setWorkflowStatus("PENDING_REVIEW");
    entity.setRejectionReason(null);
    entity.setUpdatedBy(scope.userId());
    ComplianceDocumentEntity saved = repository.save(entity);
    audit(saved, scope, "SUBMIT", "Submitted for review");
    return toResponse(saved);
  }

  @Transactional
  public ComplianceDocumentResponse approve(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requireApprover(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    entity.setWorkflowStatus("APPROVED");
    entity.setRejectionReason(null);
    entity.setUpdatedBy(scope.userId());
    ComplianceDocumentEntity saved = repository.save(entity);
    audit(saved, scope, "APPROVE", "Approved");
    return toResponse(saved);
  }

  @Transactional
  public ComplianceDocumentResponse reject(Long id, String reason) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requireApprover(scope);
    if (reason == null || reason.isBlank()) {
      throw new ComplianceException("REASON_REQUIRED", "Rejection reason is required", HttpStatus.BAD_REQUEST);
    }
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    entity.setWorkflowStatus("REJECTED");
    entity.setRejectionReason(reason.trim());
    entity.setUpdatedBy(scope.userId());
    ComplianceDocumentEntity saved = repository.save(entity);
    audit(saved, scope, "REJECT", reason.trim());
    return toResponse(saved);
  }

  @Transactional
  public ComplianceDocumentResponse upload(Long id, MultipartFile file, boolean asNewVersion, boolean force, String reason)
      throws IOException {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    boolean replacing = entity.getStoragePath() != null && !entity.getStoragePath().isBlank();
    if (replacing && !asNewVersion) {
      throw new ComplianceException(
          "VERSION_REQUIRED", "Upload as a new version to keep the previous file", HttpStatus.CONFLICT);
    }
    DocumentStorageService.StoredFile stored =
        storageService.store(scope.organizationId(), entity.getId(), file);
    if (!force) {
      List<ComplianceDocumentEntity> dupes =
          repository.findByOrganizationIdAndChecksumSha256AndActiveTrue(
              scope.organizationId(), stored.checksum());
      boolean other =
          dupes.stream().anyMatch(d -> !d.getId().equals(entity.getId()));
      if (other) {
        storageService.deleteQuietly(stored.storagePath());
        throw new ComplianceException(
            "DUPLICATE_FILE",
            "A file with identical content already exists. Upload again with force to keep both.",
            HttpStatus.CONFLICT);
      }
    }
    if (replacing) {
      snapshotVersion(entity, scope, reason);
      entity.setVersionNo(entity.getVersionNo() + 1);
    }
    entity.setFileName(stored.fileName());
    entity.setStoragePath(stored.storagePath());
    entity.setContentType(stored.contentType());
    entity.setFileSize(stored.fileSize());
    entity.setChecksumSha256(stored.checksum());
    entity.setVersionLabel("v" + entity.getVersionNo());
    entity.setUploadedBy(scope.userId());
    entity.setUpdatedBy(scope.userId());
    entity.setStatus(computeExpiry(entity.getExpiresOn()));
    ComplianceDocumentEntity saved = repository.save(entity);
    snapshotVersion(saved, scope, replacing ? reason : "Initial upload");
    audit(saved, scope, replacing ? "NEW_VERSION" : "UPLOAD", "v" + saved.getVersionNo());
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<DocumentVersionResponse> versions(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = requireAny(id, scope.organizationId());
    return versionRepository
        .findByDocumentIdAndOrganizationIdOrderByVersionNoDesc(entity.getId(), scope.organizationId())
        .stream()
        .map(
            v ->
                new DocumentVersionResponse(
                    v.getId(),
                    v.getVersionNo(),
                    v.getFileName(),
                    v.getContentType(),
                    v.getFileSize(),
                    v.getChangeReason(),
                    v.getUploadedBy(),
                    v.getUploadedAt(),
                    v.getVersionNo() == entity.getVersionNo()))
        .toList();
  }

  @Transactional
  public List<DocumentAuditResponse> audit(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = requireAny(id, scope.organizationId());
    audit(entity, scope, "VIEW", "Opened audit");
    return auditRepository
        .findByDocumentIdAndOrganizationIdOrderByCreatedAtDesc(entity.getId(), scope.organizationId())
        .stream()
        .map(a -> new DocumentAuditResponse(a.getId(), a.getAction(), a.getActorUserId(), a.getDetail(), a.getCreatedAt()))
        .toList();
  }

  @Transactional
  public FilePayload download(Long id) throws IOException {
    return readFile(id, null, "DOWNLOAD");
  }

  @Transactional
  public FilePayload downloadVersion(Long id, int versionNo) throws IOException {
    return readFile(id, versionNo, "DOWNLOAD");
  }

  public long countExpiringOrExpired(String organizationId) {
    List<ComplianceDocumentEntity> rows =
        repository.findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(organizationId);
    return rows.stream()
        .map(r -> computeExpiry(r.getExpiresOn()))
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

  private FilePayload readFile(Long id, Integer versionNo, String action) throws IOException {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceDocumentEntity entity = require(id, scope.organizationId());
    String path = entity.getStoragePath();
    String fileName = entity.getFileName();
    String contentType = entity.getContentType();
    if (versionNo != null && versionNo != entity.getVersionNo()) {
      ComplianceDocumentVersionEntity version =
          versionRepository
              .findByDocumentIdAndOrganizationIdAndVersionNo(entity.getId(), scope.organizationId(), versionNo)
              .orElseThrow(
                  () -> new ComplianceException("NOT_FOUND", "Version not found", HttpStatus.NOT_FOUND));
      path = version.getStoragePath();
      fileName = version.getFileName();
      contentType = version.getContentType();
    }
    Path file = storageService.resolve(path);
    byte[] bytes = Files.readAllBytes(file);
    audit(entity, scope, action, versionNo == null ? "current" : "v" + versionNo);
    String type =
        contentType == null || contentType.isBlank()
            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
            : contentType;
    String name = fileName == null || fileName.isBlank() ? "document-" + id : fileName;
    return new FilePayload(name, type, new ByteArrayResource(bytes));
  }

  private void snapshotVersion(ComplianceDocumentEntity entity, TenantScope scope, String reason) {
    if (entity.getStoragePath() == null || entity.getStoragePath().isBlank()) {
      return;
    }
    if (versionRepository
        .findByDocumentIdAndOrganizationIdAndVersionNo(
            entity.getId(), scope.organizationId(), entity.getVersionNo())
        .isPresent()) {
      return;
    }
    ComplianceDocumentVersionEntity version = new ComplianceDocumentVersionEntity();
    version.setDocumentId(entity.getId());
    version.setOrganizationId(scope.organizationId());
    version.setVersionNo(entity.getVersionNo());
    version.setFileName(entity.getFileName());
    version.setStoragePath(entity.getStoragePath());
    version.setContentType(entity.getContentType());
    version.setFileSize(entity.getFileSize());
    version.setChecksumSha256(entity.getChecksumSha256());
    version.setChangeReason(trimToNull(reason));
    version.setUploadedBy(scope.userId());
    versionRepository.save(version);
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
    entity.setCategoryCode(upperOrNull(request.categoryCode()));
    entity.setComplianceArea(upperOrNull(request.complianceArea()));
    entity.setFolderId(request.folderId());
    entity.setAcademicSessionId(
        request.academicSessionId() != null && !request.academicSessionId().isBlank()
            ? request.academicSessionId().trim()
            : null);
    if (request.visibility() != null && !request.visibility().isBlank()) {
      entity.setVisibility(request.visibility().trim().toUpperCase(Locale.ROOT));
    }
    entity.setTags(trimToNull(request.tags()));
    entity.setDescription(trimToNull(request.description()));
    entity.setRelatedEntity(trimToNull(request.relatedEntity()));
    entity.setRelatedEntityId(trimToNull(request.relatedEntityId()));
    entity.setRetentionYears(request.retentionYears());
    entity.setActive(true);
  }

  private boolean matchesStatus(ComplianceDocumentEntity row, String status) {
    if (status == null || status.isBlank()) {
      return true;
    }
    String want = status.trim().toUpperCase(Locale.ROOT);
    if ("VALID".equals(want) || "EXPIRING".equals(want) || "EXPIRED".equals(want)) {
      return want.equalsIgnoreCase(computeExpiry(row.getExpiresOn()));
    }
    if ("ARCHIVED".equals(want)) {
      return !row.isActive() || "ARCHIVED".equalsIgnoreCase(workflowOf(row));
    }
    return want.equalsIgnoreCase(workflowOf(row));
  }

  private String displayStatus(ComplianceDocumentEntity row) {
    if (!row.isActive() || "ARCHIVED".equalsIgnoreCase(workflowOf(row))) {
      return "ARCHIVED";
    }
    String workflow = workflowOf(row);
    if ("DRAFT".equals(workflow) || "PENDING_REVIEW".equals(workflow) || "REJECTED".equals(workflow)) {
      return workflow;
    }
    String expiry = computeExpiry(row.getExpiresOn());
    if ("EXPIRED".equals(expiry) || "EXPIRING".equals(expiry)) {
      return expiry;
    }
    return "APPROVED".equals(workflow) ? "APPROVED" : expiry;
  }

  private String computeExpiry(LocalDate expiresOn) {
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

  private String expiryBand(LocalDate expiresOn) {
    if (expiresOn == null) {
      return "NONE";
    }
    long days = ChronoUnit.DAYS.between(LocalDate.now(), expiresOn);
    if (days < 0) {
      return "EXPIRED";
    }
    if (days == 0) {
      return "TODAY";
    }
    if (days <= 7) {
      return "DAYS_7";
    }
    if (days <= 30) {
      return "DAYS_30";
    }
    if (days <= 60) {
      return "DAYS_60";
    }
    if (days <= 90) {
      return "DAYS_90";
    }
    return "LATER";
  }

  private ComplianceDocumentResponse toResponse(ComplianceDocumentEntity e) {
    Integer days =
        e.getExpiresOn() == null
            ? null
            : (int) ChronoUnit.DAYS.between(LocalDate.now(), e.getExpiresOn());
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
        displayStatus(e),
        e.getExternalUrl(),
        e.getFileName(),
        e.getContentType(),
        e.getFileSize(),
        e.getStoragePath() != null && !e.getStoragePath().isBlank(),
        e.getVersionLabel() == null ? "v" + e.getVersionNo() : e.getVersionLabel(),
        e.getNotes(),
        e.isActive(),
        e.getUpdatedAt(),
        e.getPublicCode(),
        e.getCategoryCode(),
        e.getComplianceArea(),
        e.getFolderId(),
        e.getAcademicSessionId(),
        e.getVisibility(),
        workflowOf(e),
        e.getVersionNo(),
        e.getTags(),
        e.getDescription(),
        e.getRejectionReason(),
        e.getRelatedEntity(),
        e.getRelatedEntityId(),
        days,
        expiryBand(e.getExpiresOn()));
  }

  private void ensureCategories(String organizationId) {
    if (categoryRepository.countByOrganizationId(organizationId) > 0) {
      return;
    }
    int order = 0;
    for (String[] row : DEFAULT_CATEGORIES) {
      ComplianceDocumentCategoryEntity category = new ComplianceDocumentCategoryEntity();
      category.setOrganizationId(organizationId);
      category.setCode(row[0]);
      category.setName(row[1]);
      category.setComplianceArea(row[2]);
      category.setSortOrder(order++);
      categoryRepository.save(category);
    }
  }

  private String nextPublicCode(String organizationId) {
    long seq = repository.countByOrganizationId(organizationId) + 1;
    return "DOC-" + LocalDate.now().getYear() + "-" + String.format("%06d", seq);
  }

  private boolean visibleCampus(ComplianceDocumentEntity row, TenantScope scope) {
    if (canApprove(scope) || scope.branchId() == null || scope.branchId().isBlank()) {
      return true;
    }
    return row.getBranchId() == null
        || row.getBranchId().isBlank()
        || row.getBranchId().equals(scope.branchId());
  }

  private void requireApprover(TenantScope scope) {
    if (!canApprove(scope)) {
      throw new ComplianceException(
          "FORBIDDEN", "This role cannot approve or reject documents", HttpStatus.FORBIDDEN);
    }
  }

  private boolean canApprove(TenantScope scope) {
    String role = scope.roleCode() == null ? "" : scope.roleCode().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    return APPROVER_ROLES.contains(role);
  }

  private String searchBlob(ComplianceDocumentEntity row) {
    return String.join(
            " ",
            nullToEmpty(row.getTitle()),
            nullToEmpty(row.getReferenceNo()),
            nullToEmpty(row.getDocType()),
            nullToEmpty(row.getIssuer()),
            nullToEmpty(row.getNotes()),
            nullToEmpty(row.getTags()),
            nullToEmpty(row.getDescription()),
            nullToEmpty(row.getComplianceArea()),
            nullToEmpty(row.getPublicCode()))
        .toLowerCase(Locale.ROOT);
  }

  private void audit(ComplianceDocumentEntity entity, TenantScope scope, String action, String detail) {
    ComplianceDocumentAuditEntity row = new ComplianceDocumentAuditEntity();
    row.setOrganizationId(scope.organizationId());
    row.setDocumentId(entity.getId());
    row.setAction(action);
    row.setActorUserId(scope.userId());
    row.setDetail(detail);
    auditRepository.save(row);
  }

  private ComplianceDocumentEntity require(Long id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .filter(ComplianceDocumentEntity::isActive)
        .orElseThrow(
            () -> new ComplianceException("NOT_FOUND", "Compliance document not found", HttpStatus.NOT_FOUND));
  }

  private ComplianceDocumentEntity requireAny(Long id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(
            () -> new ComplianceException("NOT_FOUND", "Compliance document not found", HttpStatus.NOT_FOUND));
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private DocumentFolderResponse folderResponse(ComplianceDocumentFolderEntity folder) {
    return new DocumentFolderResponse(
        folder.getId(),
        folder.getParentId(),
        folder.getName(),
        folder.getDescription(),
        folder.getVisibility(),
        folder.getComplianceArea(),
        folder.getDepth());
  }

  private static String workflowOf(ComplianceDocumentEntity row) {
    return row.getWorkflowStatus() == null || row.getWorkflowStatus().isBlank()
        ? "APPROVED"
        : row.getWorkflowStatus();
  }

  private static String upperOrNull(String value) {
    String trimmed = trimToNull(value);
    return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  public record FilePayload(String fileName, String contentType, Resource resource) {}
}
