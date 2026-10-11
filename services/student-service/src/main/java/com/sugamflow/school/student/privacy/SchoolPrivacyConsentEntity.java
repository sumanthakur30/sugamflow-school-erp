package com.sugamflow.school.student.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_privacy_consent")
public class SchoolPrivacyConsentEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "subject_ref", nullable = false, length = 128)
  private String subjectRef;

  @Column(name = "purpose_code", nullable = false, length = 80)
  private String purposeCode;

  @Column(name = "legal_ground", nullable = false, length = 20)
  private String legalGround;

  @Column(name = "notice_code", nullable = false, length = 80)
  private String noticeCode;

  @Column(name = "notice_version", nullable = false, length = 40)
  private String noticeVersion;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(nullable = false, length = 40)
  private String channel;

  @Column(name = "granted_at")
  private Instant grantedAt;

  @Column(name = "withdrawn_at")
  private Instant withdrawnAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getSubjectRef() { return subjectRef; }
  public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
  public String getPurposeCode() { return purposeCode; }
  public void setPurposeCode(String purposeCode) { this.purposeCode = purposeCode; }
  public String getLegalGround() { return legalGround; }
  public void setLegalGround(String legalGround) { this.legalGround = legalGround; }
  public String getNoticeCode() { return noticeCode; }
  public void setNoticeCode(String noticeCode) { this.noticeCode = noticeCode; }
  public String getNoticeVersion() { return noticeVersion; }
  public void setNoticeVersion(String noticeVersion) { this.noticeVersion = noticeVersion; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getChannel() { return channel; }
  public void setChannel(String channel) { this.channel = channel; }
  public Instant getGrantedAt() { return grantedAt; }
  public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
  public Instant getWithdrawnAt() { return withdrawnAt; }
  public void setWithdrawnAt(Instant withdrawnAt) { this.withdrawnAt = withdrawnAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
