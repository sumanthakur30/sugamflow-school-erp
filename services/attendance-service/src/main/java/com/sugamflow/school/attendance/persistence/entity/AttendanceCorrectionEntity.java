package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_correction")
public class AttendanceCorrectionEntity {
  @Id private UUID id;
  @Column(name = "organization_id", nullable = false, length = 64) private String organizationId;
  @Column(name = "biometric_day_id", nullable = false) private UUID biometricDayId;
  @Column(name = "original_status", nullable = false, length = 32) private String originalStatus;
  @Column(name = "corrected_status", nullable = false, length = 32) private String correctedStatus;
  @Column(nullable = false, length = 512) private String reason;
  @Column(name = "corrected_by", length = 128) private String correctedBy;
  @Column(name = "corrected_at", nullable = false) private Instant correctedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public UUID getBiometricDayId() { return biometricDayId; }
  public void setBiometricDayId(UUID biometricDayId) { this.biometricDayId = biometricDayId; }
  public String getOriginalStatus() { return originalStatus; }
  public void setOriginalStatus(String originalStatus) { this.originalStatus = originalStatus; }
  public String getCorrectedStatus() { return correctedStatus; }
  public void setCorrectedStatus(String correctedStatus) { this.correctedStatus = correctedStatus; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public String getCorrectedBy() { return correctedBy; }
  public void setCorrectedBy(String correctedBy) { this.correctedBy = correctedBy; }
  public Instant getCorrectedAt() { return correctedAt; }
  public void setCorrectedAt(Instant correctedAt) { this.correctedAt = correctedAt; }
}
