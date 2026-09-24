package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_session")
public class AttendanceSessionEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "section_id", nullable = false)
  private UUID sectionId;

  @Column(name = "period_id")
  private UUID periodId;

  @Column(name = "attendance_date", nullable = false)
  private LocalDate attendanceDate;

  @Column(nullable = false, length = 32)
  private String status = "DRAFT";

  @Column(name = "marked_by", length = 128)
  private String markedBy;

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
  public UUID getSectionId() { return sectionId; }
  public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
  public UUID getPeriodId() { return periodId; }
  public void setPeriodId(UUID periodId) { this.periodId = periodId; }
  public LocalDate getAttendanceDate() { return attendanceDate; }
  public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getMarkedBy() { return markedBy; }
  public void setMarkedBy(String markedBy) { this.markedBy = markedBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
