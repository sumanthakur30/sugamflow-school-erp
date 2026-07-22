package com.sugamflow.school.academic.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A daily period definition (e.g. Period 1, 09:00-09:45). */
@Entity
@Table(name = "timetable_period")
public class TimetablePeriodEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "period_no", nullable = false)
  private int periodNo;

  @Column(nullable = false, length = 64)
  private String label;

  @Column(name = "start_time", length = 8)
  private String startTime;

  @Column(name = "end_time", length = 8)
  private String endTime;

  @Column(name = "is_break", nullable = false)
  private boolean breakPeriod;

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
  public int getPeriodNo() { return periodNo; }
  public void setPeriodNo(int periodNo) { this.periodNo = periodNo; }
  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }
  public String getStartTime() { return startTime; }
  public void setStartTime(String startTime) { this.startTime = startTime; }
  public String getEndTime() { return endTime; }
  public void setEndTime(String endTime) { this.endTime = endTime; }
  public boolean isBreakPeriod() { return breakPeriod; }
  public void setBreakPeriod(boolean breakPeriod) { this.breakPeriod = breakPeriod; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
