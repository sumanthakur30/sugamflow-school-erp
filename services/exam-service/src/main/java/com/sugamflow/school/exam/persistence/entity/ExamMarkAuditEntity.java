package com.sugamflow.school.exam.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_mark_audit")
public class ExamMarkAuditEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "exam_definition_id", nullable = false)
  private UUID examDefinitionId;

  @Column(name = "student_id")
  private UUID studentId;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "student_name", length = 191)
  private String studentName;

  @Column(name = "previous_marks", precision = 10, scale = 2)
  private BigDecimal previousMarks;

  @Column(name = "new_marks", precision = 10, scale = 2)
  private BigDecimal newMarks;

  @Column(name = "previous_status", length = 32)
  private String previousStatus;

  @Column(name = "new_status", length = 32)
  private String newStatus;

  @Column(length = 500)
  private String reason;

  @Column(name = "changed_by", length = 128)
  private String changedBy;

  @Column(name = "changed_at", nullable = false)
  private Instant changedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public UUID getExamDefinitionId() { return examDefinitionId; }
  public void setExamDefinitionId(UUID examDefinitionId) { this.examDefinitionId = examDefinitionId; }
  public UUID getStudentId() { return studentId; }
  public void setStudentId(UUID studentId) { this.studentId = studentId; }
  public String getAdmissionNo() { return admissionNo; }
  public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
  public String getStudentName() { return studentName; }
  public void setStudentName(String studentName) { this.studentName = studentName; }
  public BigDecimal getPreviousMarks() { return previousMarks; }
  public void setPreviousMarks(BigDecimal previousMarks) { this.previousMarks = previousMarks; }
  public BigDecimal getNewMarks() { return newMarks; }
  public void setNewMarks(BigDecimal newMarks) { this.newMarks = newMarks; }
  public String getPreviousStatus() { return previousStatus; }
  public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
  public String getNewStatus() { return newStatus; }
  public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getChangedBy() { return changedBy; }
  public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
  public Instant getChangedAt() { return changedAt; }
  public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
