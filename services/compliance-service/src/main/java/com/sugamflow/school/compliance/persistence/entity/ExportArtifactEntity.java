package com.sugamflow.school.compliance.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "export_artifact")
public class ExportArtifactEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 100)
  private String organizationId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "artifact_key", nullable = false, length = 80)
  private String artifactKey;

  @Column(name = "format_code", nullable = false, length = 20)
  private String formatCode;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "storage_path", nullable = false, length = 1000)
  private String storagePath;

  @Column(name = "content_type", nullable = false, length = 120)
  private String contentType;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "checksum_sha256", length = 64)
  private String checksumSha256;

  @Column(name = "created_by", length = 120)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
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

  public Long getCampaignId() {
    return campaignId;
  }

  public void setCampaignId(Long campaignId) {
    this.campaignId = campaignId;
  }

  public String getArtifactKey() {
    return artifactKey;
  }

  public void setArtifactKey(String artifactKey) {
    this.artifactKey = artifactKey;
  }

  public String getFormatCode() {
    return formatCode;
  }

  public void setFormatCode(String formatCode) {
    this.formatCode = formatCode;
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

  public long getFileSize() {
    return fileSize;
  }

  public void setFileSize(long fileSize) {
    this.fileSize = fileSize;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public void setChecksumSha256(String checksumSha256) {
    this.checksumSha256 = checksumSha256;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
