package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_mark")
public class AttendanceMarkEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "student_id")
  private UUID studentId;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "student_name", length = 191)
  private String studentName;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(length = 512)
  private String remark;

  @Column(name = "marked_by", length = 128)
  private String markedBy;

  @Column(name = "marked_at", nullable = false)
  private Instant markedAt = Instant.now();

  @Column(name = "last_notified_status", length = 32)
  private String lastNotifiedStatus;

  @Column(name = "last_notified_at")
  private Instant lastNotifiedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public UUID getSessionId() { return sessionId; }
  public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
  public UUID getStudentId() { return studentId; }
  public void setStudentId(UUID studentId) { this.studentId = studentId; }
  public String getAdmissionNo() { return admissionNo; }
  public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
  public String getStudentName() { return studentName; }
  public void setStudentName(String studentName) { this.studentName = studentName; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getRemark() { return remark; }
  public void setRemark(String remark) { this.remark = remark; }
  public String getMarkedBy() { return markedBy; }
  public void setMarkedBy(String markedBy) { this.markedBy = markedBy; }
  public Instant getMarkedAt() { return markedAt; }
  public void setMarkedAt(Instant markedAt) { this.markedAt = markedAt; }
  public String getLastNotifiedStatus() { return lastNotifiedStatus; }
  public void setLastNotifiedStatus(String lastNotifiedStatus) { this.lastNotifiedStatus = lastNotifiedStatus; }
  public Instant getLastNotifiedAt() { return lastNotifiedAt; }
  public void setLastNotifiedAt(Instant lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
