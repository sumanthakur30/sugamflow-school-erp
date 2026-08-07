package com.sugamflow.school.compliance.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "compliance_import_job")
public class ComplianceImportJobEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 100)
  private String organizationId;

  @Column(name = "board_code", nullable = false, length = 40)
  private String boardCode;

  @Column(name = "pack_key", length = 80)
  private String packKey;

  @Column(name = "entity_type", nullable = false, length = 40)
  private String entityType;

  @Column(name = "file_name")
  private String fileName;

  @Column(nullable = false, length = 40)
  private String status = "DRAFT";

  @Column(name = "fill_blank_only", nullable = false)
  private boolean fillBlankOnly = true;

  @Column(name = "total_rows", nullable = false)
  private int totalRows;

  @Column(name = "ready_count", nullable = false)
  private int readyCount;

  @Column(name = "error_count", nullable = false)
  private int errorCount;

  @Column(name = "matched_count", nullable = false)
  private int matchedCount;

  @Column(name = "updated_count", nullable = false)
  private int updatedCount;

  @Column(name = "created_by", length = 120)
  private String createdBy;

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

  public String getBoardCode() {
    return boardCode;
  }

  public void setBoardCode(String boardCode) {
    this.boardCode = boardCode;
  }

  public String getPackKey() {
    return packKey;
  }

  public void setPackKey(String packKey) {
    this.packKey = packKey;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public boolean isFillBlankOnly() {
    return fillBlankOnly;
  }

  public void setFillBlankOnly(boolean fillBlankOnly) {
    this.fillBlankOnly = fillBlankOnly;
  }

  public int getTotalRows() {
    return totalRows;
  }

  public void setTotalRows(int totalRows) {
    this.totalRows = totalRows;
  }

  public int getReadyCount() {
    return readyCount;
  }

  public void setReadyCount(int readyCount) {
    this.readyCount = readyCount;
  }

  public int getErrorCount() {
    return errorCount;
  }

  public void setErrorCount(int errorCount) {
    this.errorCount = errorCount;
  }

  public int getMatchedCount() {
    return matchedCount;
  }

  public void setMatchedCount(int matchedCount) {
    this.matchedCount = matchedCount;
  }

  public int getUpdatedCount() {
    return updatedCount;
  }

  public void setUpdatedCount(int updatedCount) {
    this.updatedCount = updatedCount;
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

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
