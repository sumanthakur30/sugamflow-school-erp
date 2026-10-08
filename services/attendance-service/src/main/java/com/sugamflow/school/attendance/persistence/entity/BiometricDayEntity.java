package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "biometric_day")
public class BiometricDayEntity {
  @Id private UUID id;
  @Column(name = "organization_id", nullable = false, length = 64) private String organizationId;
  @Column(name = "branch_id", length = 64) private String branchId;
  @Column(name = "person_type", nullable = false, length = 16) private String personType;
  @Column(name = "person_code", nullable = false, length = 64) private String personCode;
  @Column(name = "display_name", length = 191) private String displayName;
  @Column(name = "class_section", length = 64) private String classSection;
  @Column(name = "attendance_date", nullable = false) private LocalDate attendanceDate;
  @Column(name = "first_in") private Instant firstIn;
  @Column(name = "last_out") private Instant lastOut;
  @Column(nullable = false, length = 32) private String status;
  @Column(name = "late_minutes", nullable = false) private int lateMinutes;
  @Column(name = "early_minutes", nullable = false) private int earlyMinutes;
  @Column(name = "working_minutes", nullable = false) private int workingMinutes;
  @Column(name = "attendance_mark_id") private UUID attendanceMarkId;
  @Column(nullable = false) private boolean corrected;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getPersonType() { return personType; }
  public void setPersonType(String personType) { this.personType = personType; }
  public String getPersonCode() { return personCode; }
  public void setPersonCode(String personCode) { this.personCode = personCode; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getClassSection() { return classSection; }
  public void setClassSection(String classSection) { this.classSection = classSection; }
  public LocalDate getAttendanceDate() { return attendanceDate; }
  public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }
  public Instant getFirstIn() { return firstIn; }
  public void setFirstIn(Instant firstIn) { this.firstIn = firstIn; }
  public Instant getLastOut() { return lastOut; }
  public void setLastOut(Instant lastOut) { this.lastOut = lastOut; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getLateMinutes() { return lateMinutes; }
  public void setLateMinutes(int lateMinutes) { this.lateMinutes = lateMinutes; }
  public int getEarlyMinutes() { return earlyMinutes; }
  public void setEarlyMinutes(int earlyMinutes) { this.earlyMinutes = earlyMinutes; }
  public int getWorkingMinutes() { return workingMinutes; }
  public void setWorkingMinutes(int workingMinutes) { this.workingMinutes = workingMinutes; }
  public UUID getAttendanceMarkId() { return attendanceMarkId; }
  public void setAttendanceMarkId(UUID attendanceMarkId) { this.attendanceMarkId = attendanceMarkId; }
  public boolean isCorrected() { return corrected; }
  public void setCorrected(boolean corrected) { this.corrected = corrected; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
