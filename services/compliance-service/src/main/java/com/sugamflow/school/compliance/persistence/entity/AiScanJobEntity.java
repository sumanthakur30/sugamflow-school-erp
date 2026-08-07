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
@Table(name = "ai_scan_job")
public class AiScanJobEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 100)
  private String organizationId;

  @Column(name = "campaign_id")
  private Long campaignId;

  @Column(nullable = false, length = 40)
  private String status = "QUEUED";

  @Column(nullable = false, length = 40)
  private String provider = "heuristic";

  @Column(name = "warn_count", nullable = false)
  private int warnCount;

  @Column(name = "records_scanned", nullable = false)
  private int recordsScanned;

  @Column(name = "result_message", columnDefinition = "TEXT")
  private String resultMessage;

  @Column(name = "fallback_used", nullable = false)
  private boolean fallbackUsed;

  @Column(name = "created_by", length = 120)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public int getWarnCount() {
    return warnCount;
  }

  public void setWarnCount(int warnCount) {
    this.warnCount = warnCount;
  }

  public int getRecordsScanned() {
    return recordsScanned;
  }

  public void setRecordsScanned(int recordsScanned) {
    this.recordsScanned = recordsScanned;
  }

  public String getResultMessage() {
    return resultMessage;
  }

  public void setResultMessage(String resultMessage) {
    this.resultMessage = resultMessage;
  }

  public boolean isFallbackUsed() {
    return fallbackUsed;
  }

  public void setFallbackUsed(boolean fallbackUsed) {
    this.fallbackUsed = fallbackUsed;
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

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
