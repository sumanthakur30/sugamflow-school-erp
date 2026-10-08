package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "biometric_enrollment")
public class BiometricEnrollmentEntity {
  @Id private UUID id;
  @Column(name = "organization_id", nullable = false, length = 64) private String organizationId;
  @Column(name = "branch_id", length = 64) private String branchId;
  @Column(name = "person_type", nullable = false, length = 16) private String personType;
  @Column(name = "person_id", length = 64) private String personId;
  @Column(name = "person_code", length = 64) private String personCode;
  @Column(name = "display_name", nullable = false, length = 191) private String displayName;
  @Column(name = "class_section", length = 64) private String classSection;
  @Column(name = "section_id") private UUID sectionId;
  @Column(name = "enrollment_code", nullable = false, length = 64) private String enrollmentCode;
  @Column(name = "verification_type", nullable = false, length = 32) private String verificationType = "FINGERPRINT";
  @Column(nullable = false, length = 32) private String status = "ACTIVE";
  @Column(name = "notify_mobile", length = 32) private String notifyMobile;
  @Column(name = "notify_email", length = 191) private String notifyEmail;
  @Column(name = "device_id", length = 64) private String deviceId;
  @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getPersonType() { return personType; }
  public void setPersonType(String personType) { this.personType = personType; }
  public String getPersonId() { return personId; }
  public void setPersonId(String personId) { this.personId = personId; }
  public String getPersonCode() { return personCode; }
  public void setPersonCode(String personCode) { this.personCode = personCode; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getClassSection() { return classSection; }
  public void setClassSection(String classSection) { this.classSection = classSection; }
  public UUID getSectionId() { return sectionId; }
  public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
  public String getEnrollmentCode() { return enrollmentCode; }
  public void setEnrollmentCode(String enrollmentCode) { this.enrollmentCode = enrollmentCode; }
  public String getVerificationType() { return verificationType; }
  public void setVerificationType(String verificationType) { this.verificationType = verificationType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getNotifyMobile() { return notifyMobile; }
  public void setNotifyMobile(String notifyMobile) { this.notifyMobile = notifyMobile; }
  public String getNotifyEmail() { return notifyEmail; }
  public void setNotifyEmail(String notifyEmail) { this.notifyEmail = notifyEmail; }
  public String getDeviceId() { return deviceId; }
  public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
