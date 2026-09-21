package com.sugamflow.school.exam.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lms_homework_submission")
public class HomeworkSubmissionEntity {

  public static final String STATUS_SUBMITTED = "SUBMITTED";
  public static final String STATUS_GRADED = "GRADED";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "homework_id", nullable = false)
  private UUID homeworkId;

  @Column(name = "student_id")
  private UUID studentId;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "student_name", length = 191)
  private String studentName;

  @Column(columnDefinition = "TEXT")
  private String body;

  @Column(nullable = false, length = 32)
  private String status = STATUS_SUBMITTED;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt = Instant.now();

  @Column(name = "graded_marks", precision = 10, scale = 2)
  private BigDecimal gradedMarks;

  @Column(columnDefinition = "TEXT")
  private String feedback;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public UUID getHomeworkId() { return homeworkId; }
  public void setHomeworkId(UUID homeworkId) { this.homeworkId = homeworkId; }
  public UUID getStudentId() { return studentId; }
  public void setStudentId(UUID studentId) { this.studentId = studentId; }
  public String getAdmissionNo() { return admissionNo; }
  public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
  public String getStudentName() { return studentName; }
  public void setStudentName(String studentName) { this.studentName = studentName; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getSubmittedAt() { return submittedAt; }
  public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
  public BigDecimal getGradedMarks() { return gradedMarks; }
  public void setGradedMarks(BigDecimal gradedMarks) { this.gradedMarks = gradedMarks; }
  public String getFeedback() { return feedback; }
  public void setFeedback(String feedback) { this.feedback = feedback; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
