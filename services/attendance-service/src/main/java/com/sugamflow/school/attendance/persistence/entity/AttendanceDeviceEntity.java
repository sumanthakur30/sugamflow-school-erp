package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "attendance_device")
public class AttendanceDeviceEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "device_key", nullable = false, length = 128)
  private String deviceKey;

  @Column(nullable = false, length = 256)
  private String name;

  @Column(name = "adapter_type", nullable = false, length = 64)
  private String adapterType;

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> configJson = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(length = 32)
  private String vendor;

  @Column(name = "serial_number", length = 128)
  private String serialNumber;

  @Column(name = "device_type", length = 32)
  private String deviceType;

  @Column(length = 128)
  private String location;

  @Column(name = "gate_name", length = 128)
  private String gateName;

  @Column(length = 64)
  private String firmware;

  @Column(nullable = false, length = 16)
  private String direction = "BOTH";

  @Column(name = "time_zone", nullable = false, length = 64)
  private String timeZone = "Asia/Kolkata";

  @Column(name = "credential_hash", length = 128)
  private String credentialHash;

  @Column(name = "credential_prefix", length = 16)
  private String credentialPrefix;

  @Column(name = "last_heartbeat_at")
  private Instant lastHeartbeatAt;

  @Column(name = "last_event_at")
  private Instant lastEventAt;

  @Column(name = "heartbeat_timeout_seconds", nullable = false)
  private int heartbeatTimeoutSeconds = 120;

  @Column(name = "error_count", nullable = false)
  private int errorCount;

  @Column(name = "last_device_time")
  private Instant lastDeviceTime;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getDeviceKey() { return deviceKey; }
  public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getAdapterType() { return adapterType; }
  public void setAdapterType(String adapterType) { this.adapterType = adapterType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Map<String, Object> getConfigJson() { return configJson; }
  public void setConfigJson(Map<String, Object> configJson) { this.configJson = configJson; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public String getVendor() { return vendor; }
  public void setVendor(String vendor) { this.vendor = vendor; }
  public String getSerialNumber() { return serialNumber; }
  public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
  public String getDeviceType() { return deviceType; }
  public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
  public String getLocation() { return location; }
  public void setLocation(String location) { this.location = location; }
  public String getGateName() { return gateName; }
  public void setGateName(String gateName) { this.gateName = gateName; }
  public String getFirmware() { return firmware; }
  public void setFirmware(String firmware) { this.firmware = firmware; }
  public String getDirection() { return direction; }
  public void setDirection(String direction) { this.direction = direction; }
  public String getTimeZone() { return timeZone; }
  public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
  public String getCredentialHash() { return credentialHash; }
  public void setCredentialHash(String credentialHash) { this.credentialHash = credentialHash; }
  public String getCredentialPrefix() { return credentialPrefix; }
  public void setCredentialPrefix(String credentialPrefix) { this.credentialPrefix = credentialPrefix; }
  public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
  public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
  public Instant getLastEventAt() { return lastEventAt; }
  public void setLastEventAt(Instant lastEventAt) { this.lastEventAt = lastEventAt; }
  public int getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }
  public void setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) { this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds; }
  public int getErrorCount() { return errorCount; }
  public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
  public Instant getLastDeviceTime() { return lastDeviceTime; }
  public void setLastDeviceTime(Instant lastDeviceTime) { this.lastDeviceTime = lastDeviceTime; }
}
