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
@Table(name = "attendance_device_event")
public class AttendanceDeviceEventEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "device_id", nullable = false, length = 64)
  private String deviceId;

  @Column(name = "adapter_type", nullable = false, length = 64)
  private String adapterType;

  @Column(nullable = false, length = 32)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadJson = new LinkedHashMap<>();

  @Column(name = "attendance_record_id", length = 64)
  private String attendanceRecordId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getDeviceId() { return deviceId; }
  public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
  public String getAdapterType() { return adapterType; }
  public void setAdapterType(String adapterType) { this.adapterType = adapterType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Map<String, Object> getPayloadJson() { return payloadJson; }
  public void setPayloadJson(Map<String, Object> payloadJson) { this.payloadJson = payloadJson; }
  public String getAttendanceRecordId() { return attendanceRecordId; }
  public void setAttendanceRecordId(String attendanceRecordId) {
    this.attendanceRecordId = attendanceRecordId;
  }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
