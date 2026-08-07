package com.sugamflow.school.compliance.persistence.entity;

import java.math.BigDecimal;
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
@Table(name = "submission_campaign")
public class SubmissionCampaignEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 100)
  private String organizationId;

  @Column(name = "academic_session_id", length = 100)
  private String academicSessionId;

  @Column(name = "board_code", nullable = false, length = 40)
  private String boardCode = "CBSE";

  @Column(name = "pack_key", length = 80)
  private String packKey;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 40)
  private String status = "DRAFT";

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(name = "compliance_score")
  private BigDecimal complianceScore;

  @Column(name = "blocker_count", nullable = false)
  private int blockerCount;

  @Column(name = "warn_count", nullable = false)
  private int warnCount;

  @Column(name = "require_management_approval", nullable = false)
  private boolean requireManagementApproval;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "locked_by", length = 120)
  private String lockedBy;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "submitted_by", length = 120)
  private String submittedBy;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "archived_by", length = 120)
  private String archivedBy;

  @Column(name = "adapter_channel", length = 40)
  private String adapterChannel;

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

  public String getAcademicSessionId() {
    return academicSessionId;
  }

  public void setAcademicSessionId(String academicSessionId) {
    this.academicSessionId = academicSessionId;
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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public void setDueAt(Instant dueAt) {
    this.dueAt = dueAt;
  }

  public BigDecimal getComplianceScore() {
    return complianceScore;
  }

  public void setComplianceScore(BigDecimal complianceScore) {
    this.complianceScore = complianceScore;
  }

  public int getBlockerCount() {
    return blockerCount;
  }

  public void setBlockerCount(int blockerCount) {
    this.blockerCount = blockerCount;
  }

  public int getWarnCount() {
    return warnCount;
  }

  public void setWarnCount(int warnCount) {
    this.warnCount = warnCount;
  }

  public boolean isRequireManagementApproval() {
    return requireManagementApproval;
  }

  public void setRequireManagementApproval(boolean requireManagementApproval) {
    this.requireManagementApproval = requireManagementApproval;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }

  public void setLockedAt(Instant lockedAt) {
    this.lockedAt = lockedAt;
  }

  public String getLockedBy() {
    return lockedBy;
  }

  public void setLockedBy(String lockedBy) {
    this.lockedBy = lockedBy;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Instant submittedAt) {
    this.submittedAt = submittedAt;
  }

  public String getSubmittedBy() {
    return submittedBy;
  }

  public void setSubmittedBy(String submittedBy) {
    this.submittedBy = submittedBy;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Instant archivedAt) {
    this.archivedAt = archivedAt;
  }

  public String getArchivedBy() {
    return archivedBy;
  }

  public void setArchivedBy(String archivedBy) {
    this.archivedBy = archivedBy;
  }

  public String getAdapterChannel() {
    return adapterChannel;
  }

  public void setAdapterChannel(String adapterChannel) {
    this.adapterChannel = adapterChannel;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
