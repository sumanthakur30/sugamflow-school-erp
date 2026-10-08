package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "biometric_command")
public class BiometricCommandEntity {
  @Id private UUID id;
  @Column(name = "organization_id", nullable = false, length = 64) private String organizationId;
  @Column(name = "device_id", nullable = false, length = 64) private String deviceId;
  @Column(name = "command_text", nullable = false, length = 256) private String commandText;
  @Column(nullable = false) private boolean dangerous;
  @Column(name = "requested_by", length = 128) private String requestedBy;
  @Column(name = "requested_at", nullable = false) private Instant requestedAt = Instant.now();
  @Column(nullable = false, length = 32) private String status;
  @Column(length = 1024) private String response;
  @Column(name = "completed_at") private Instant completedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getDeviceId() { return deviceId; }
  public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
  public String getCommandText() { return commandText; }
  public void setCommandText(String commandText) { this.commandText = commandText; }
  public boolean isDangerous() { return dangerous; }
  public void setDangerous(boolean dangerous) { this.dangerous = dangerous; }
  public String getRequestedBy() { return requestedBy; }
  public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
  public Instant getRequestedAt() { return requestedAt; }
  public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getResponse() { return response; }
  public void setResponse(String response) { this.response = response; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
