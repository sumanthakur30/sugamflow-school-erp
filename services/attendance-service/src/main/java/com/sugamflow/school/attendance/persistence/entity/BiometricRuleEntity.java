package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "biometric_rule")
public class BiometricRuleEntity {
  @Id private UUID id;
  @Column(name = "organization_id", nullable = false, length = 64) private String organizationId;
  @Column(name = "branch_id", nullable = false, length = 64) private String branchId = "";
  @Column(name = "punch_mode", nullable = false, length = 32) private String punchMode = "FIRST_LAST";
  @Column(name = "school_start", nullable = false) private LocalTime schoolStart = LocalTime.of(8, 30);
  @Column(name = "grace_minutes", nullable = false) private int graceMinutes = 10;
  @Column(name = "school_end", nullable = false) private LocalTime schoolEnd = LocalTime.of(15, 0);
  @Column(name = "split_time", nullable = false) private LocalTime splitTime = LocalTime.NOON;
  @Column(name = "half_day_minutes", nullable = false) private int halfDayMinutes = 240;
  @Column(name = "time_zone", nullable = false, length = 64) private String timeZone = "Asia/Kolkata";
  @Column(name = "drift_threshold_seconds", nullable = false) private int driftThresholdSeconds = 120;
  @Column(name = "notify_on_check_in", nullable = false) private boolean notifyOnCheckIn = true;
  @Column(name = "notify_channels", nullable = false, length = 128) private String notifyChannels = "IN_APP";
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getPunchMode() { return punchMode; }
  public void setPunchMode(String punchMode) { this.punchMode = punchMode; }
  public LocalTime getSchoolStart() { return schoolStart; }
  public void setSchoolStart(LocalTime schoolStart) { this.schoolStart = schoolStart; }
  public int getGraceMinutes() { return graceMinutes; }
  public void setGraceMinutes(int graceMinutes) { this.graceMinutes = graceMinutes; }
  public LocalTime getSchoolEnd() { return schoolEnd; }
  public void setSchoolEnd(LocalTime schoolEnd) { this.schoolEnd = schoolEnd; }
  public LocalTime getSplitTime() { return splitTime; }
  public void setSplitTime(LocalTime splitTime) { this.splitTime = splitTime; }
  public int getHalfDayMinutes() { return halfDayMinutes; }
  public void setHalfDayMinutes(int halfDayMinutes) { this.halfDayMinutes = halfDayMinutes; }
  public String getTimeZone() { return timeZone; }
  public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
  public int getDriftThresholdSeconds() { return driftThresholdSeconds; }
  public void setDriftThresholdSeconds(int driftThresholdSeconds) { this.driftThresholdSeconds = driftThresholdSeconds; }
  public boolean isNotifyOnCheckIn() { return notifyOnCheckIn; }
  public void setNotifyOnCheckIn(boolean notifyOnCheckIn) { this.notifyOnCheckIn = notifyOnCheckIn; }
  public String getNotifyChannels() { return notifyChannels; }
  public void setNotifyChannels(String notifyChannels) { this.notifyChannels = notifyChannels; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
