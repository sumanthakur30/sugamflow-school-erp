package com.sugamflow.school.academic.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Working week and holiday list for one campus and academic session. */
@Entity
@Table(name = "academic_calendar")
public class AcademicCalendarEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", nullable = false, length = 64)
  private String branchId = "";

  @Column(name = "academic_session_id", nullable = false, length = 64)
  private String academicSessionId = "";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "working_days", nullable = false, columnDefinition = "jsonb")
  private List<String> workingDays = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> holidays = new ArrayList<>();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getBranchId() {
    return branchId;
  }

  public void setBranchId(String branchId) {
    this.branchId = branchId;
  }

  public String getAcademicSessionId() {
    return academicSessionId;
  }

  public void setAcademicSessionId(String academicSessionId) {
    this.academicSessionId = academicSessionId;
  }

  public List<String> getWorkingDays() {
    return workingDays;
  }

  public void setWorkingDays(List<String> workingDays) {
    this.workingDays = workingDays == null ? new ArrayList<>() : workingDays;
  }

  public List<Map<String, Object>> getHolidays() {
    return holidays;
  }

  public void setHolidays(List<Map<String, Object>> holidays) {
    this.holidays = holidays == null ? new ArrayList<>() : holidays;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
