package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "biometric_event")
public class BiometricEventEntity {
  @Id private UUID id;
  @Column(name = "organization_id", nullable = false, length = 64) private String organizationId;
  @Column(name = "branch_id", length = 64) private String branchId;
  @Column(name = "device_id", length = 64) private String deviceId;
  @Column(name = "serial_number", length = 128) private String serialNumber;
  @Column(name = "person_code", length = 64) private String personCode;
  @Column(name = "event_time", nullable = false) private Instant eventTime;
  @Column(name = "event_type", length = 32) private String eventType;
  @Column(name = "verification_type", length = 32) private String verificationType;
  @Column(nullable = false, length = 32) private String source = "BIOMETRIC";
  @Column(nullable = false, length = 32) private String status;
  @Column(name = "error_message", length = 512) private String errorMessage;
  @Column(name = "event_hash", nullable = false, length = 64) private String eventHash;
  @Column(name = "source_event_id", length = 128) private String sourceEventId;
  @Column(name = "received_at", nullable = false) private Instant receivedAt = Instant.now();
  @Column(name = "processed_at") private Instant processedAt;
  @Column(nullable = false) private boolean notified;
  @Column(name = "retry_count", nullable = false) private int retryCount;

  @Transient private boolean duplicateReplay;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getDeviceId() { return deviceId; }
  public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
  public String getSerialNumber() { return serialNumber; }
  public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
  public String getPersonCode() { return personCode; }
  public void setPersonCode(String personCode) { this.personCode = personCode; }
  public Instant getEventTime() { return eventTime; }
  public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getVerificationType() { return verificationType; }
  public void setVerificationType(String verificationType) { this.verificationType = verificationType; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public String getEventHash() { return eventHash; }
  public void setEventHash(String eventHash) { this.eventHash = eventHash; }
  public String getSourceEventId() { return sourceEventId; }
  public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
  public Instant getReceivedAt() { return receivedAt; }
  public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
  public Instant getProcessedAt() { return processedAt; }
  public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
  public boolean isNotified() { return notified; }
  public void setNotified(boolean notified) { this.notified = notified; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
  public boolean isDuplicateReplay() { return duplicateReplay; }
  public void setDuplicateReplay(boolean duplicateReplay) { this.duplicateReplay = duplicateReplay; }
}
