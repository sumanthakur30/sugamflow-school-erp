package com.sugamflow.school.fee.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fee_due_reminder_outbox")
public class FeeDueReminderOutboxEntity {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_SENT = "SENT";
  public static final String STATUS_FAILED = "FAILED";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "student_key", nullable = false, length = 128)
  private String studentKey;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "collection_id")
  private UUID collectionId;

  @Column(name = "period_key", nullable = false, length = 128)
  private String periodKey;

  @Column(precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 20)
  private String channel;

  @Column(nullable = false, length = 191)
  private String recipient;

  @Column(name = "guardian_name", length = 191)
  private String guardianName;

  @Column(nullable = false, length = 255)
  private String subject;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(nullable = false, length = 20)
  private String status = STATUS_PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 512)
  private String lastError;

  @Column(name = "notification_id", length = 64)
  private String notificationId;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getAcademicSessionId() { return academicSessionId; }
  public void setAcademicSessionId(String academicSessionId) { this.academicSessionId = academicSessionId; }
  public String getStudentKey() { return studentKey; }
  public void setStudentKey(String studentKey) { this.studentKey = studentKey; }
  public String getAdmissionNo() { return admissionNo; }
  public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
  public UUID getCollectionId() { return collectionId; }
  public void setCollectionId(UUID collectionId) { this.collectionId = collectionId; }
  public String getPeriodKey() { return periodKey; }
  public void setPeriodKey(String periodKey) { this.periodKey = periodKey; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public String getChannel() { return channel; }
  public void setChannel(String channel) { this.channel = channel; }
  public String getRecipient() { return recipient; }
  public void setRecipient(String recipient) { this.recipient = recipient; }
  public String getGuardianName() { return guardianName; }
  public void setGuardianName(String guardianName) { this.guardianName = guardianName; }
  public String getSubject() { return subject; }
  public void setSubject(String subject) { this.subject = subject; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public String getNotificationId() { return notificationId; }
  public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
  public Instant getSentAt() { return sentAt; }
  public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
