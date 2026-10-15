package com.sugamflow.school.compliance.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "compliance_document")
public class ComplianceDocumentEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 100)
  private String organizationId;

  @Column(name = "branch_id", length = 100)
  private String branchId;

  @Column(name = "doc_type", nullable = false, length = 80)
  private String docType;

  @Column(nullable = false)
  private String title;

  @Column(name = "reference_no", length = 120)
  private String referenceNo;

  private String issuer;

  @Column(name = "issued_on")
  private LocalDate issuedOn;

  @Column(name = "expires_on")
  private LocalDate expiresOn;

  @Column(nullable = false, length = 40)
  private String status = "VALID";

  @Column(name = "external_url", length = 1000)
  private String externalUrl;

  @Column(name = "file_name")
  private String fileName;

  @Column(name = "storage_path", length = 1000)
  private String storagePath;

  @Column(name = "content_type", length = 120)
  private String contentType;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "version_label", length = 40)
  private String versionLabel;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(name = "public_code", nullable = false, length = 40)
  private String publicCode;

  @Column(name = "category_code", length = 80)
  private String categoryCode;

  @Column(name = "compliance_area", length = 80)
  private String complianceArea;

  @Column(name = "folder_id")
  private Long folderId;

  @Column(name = "academic_session_id", length = 100)
  private String academicSessionId;

  @Column(nullable = false, length = 40)
  private String visibility = "SCHOOL";

  @Column(name = "workflow_status", nullable = false, length = 40)
  private String workflowStatus = "DRAFT";

  @Column(name = "version_no", nullable = false)
  private int versionNo = 1;

  @Column(length = 500)
  private String tags;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "checksum_sha256", length = 64)
  private String checksumSha256;

  @Column(name = "uploaded_by", length = 100)
  private String uploadedBy;

  @Column(name = "updated_by", length = 100)
  private String updatedBy;

  @Column(name = "rejection_reason", columnDefinition = "TEXT")
  private String rejectionReason;

  @Column(name = "related_entity", length = 80)
  private String relatedEntity;

  @Column(name = "related_entity_id", length = 100)
  private String relatedEntityId;

  @Column(name = "retention_years")
  private Integer retentionYears;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getBranchId() {
    return branchId;
  }

  public void setBranchId(String branchId) {
    this.branchId = branchId;
  }

  public String getDocType() {
    return docType;
  }

  public void setDocType(String docType) {
    this.docType = docType;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getReferenceNo() {
    return referenceNo;
  }

  public void setReferenceNo(String referenceNo) {
    this.referenceNo = referenceNo;
  }

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public LocalDate getIssuedOn() {
    return issuedOn;
  }

  public void setIssuedOn(LocalDate issuedOn) {
    this.issuedOn = issuedOn;
  }

  public LocalDate getExpiresOn() {
    return expiresOn;
  }

  public void setExpiresOn(LocalDate expiresOn) {
    this.expiresOn = expiresOn;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  public void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public Long getFileSize() {
    return fileSize;
  }

  public void setFileSize(Long fileSize) {
    this.fileSize = fileSize;
  }

  public String getVersionLabel() {
    return versionLabel;
  }

  public void setVersionLabel(String versionLabel) {
    this.versionLabel = versionLabel;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public String getPublicCode() {
    return publicCode;
  }

  public void setPublicCode(String publicCode) {
    this.publicCode = publicCode;
  }

  public String getCategoryCode() {
    return categoryCode;
  }

  public void setCategoryCode(String categoryCode) {
    this.categoryCode = categoryCode;
  }

  public String getComplianceArea() {
    return complianceArea;
  }

  public void setComplianceArea(String complianceArea) {
    this.complianceArea = complianceArea;
  }

  public Long getFolderId() {
    return folderId;
  }

  public void setFolderId(Long folderId) {
    this.folderId = folderId;
  }

  public String getAcademicSessionId() {
    return academicSessionId;
  }

  public void setAcademicSessionId(String academicSessionId) {
    this.academicSessionId = academicSessionId;
  }

  public String getVisibility() {
    return visibility;
  }

  public void setVisibility(String visibility) {
    this.visibility = visibility;
  }

  public String getWorkflowStatus() {
    return workflowStatus;
  }

  public void setWorkflowStatus(String workflowStatus) {
    this.workflowStatus = workflowStatus;
  }

  public int getVersionNo() {
    return versionNo;
  }

  public void setVersionNo(int versionNo) {
    this.versionNo = versionNo;
  }

  public String getTags() {
    return tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public void setChecksumSha256(String checksumSha256) {
    this.checksumSha256 = checksumSha256;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(String uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public String getRelatedEntity() {
    return relatedEntity;
  }

  public void setRelatedEntity(String relatedEntity) {
    this.relatedEntity = relatedEntity;
  }

  public String getRelatedEntityId() {
    return relatedEntityId;
  }

  public void setRelatedEntityId(String relatedEntityId) {
    this.relatedEntityId = relatedEntityId;
  }

  public Integer getRetentionYears() {
    return retentionYears;
  }

  public void setRetentionYears(Integer retentionYears) {
    this.retentionYears = retentionYears;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
