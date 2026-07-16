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
}
