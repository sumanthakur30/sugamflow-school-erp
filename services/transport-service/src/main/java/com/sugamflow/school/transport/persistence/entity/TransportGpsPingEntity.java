package com.sugamflow.school.transport.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transport_gps_ping")
public class TransportGpsPingEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "vehicle_no", nullable = false, length = 64)
  private String vehicleNo;

  @Column(nullable = false, precision = 10, scale = 6)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 10, scale = 6)
  private BigDecimal longitude;

  @Column(length = 256)
  private String note;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getVehicleNo() { return vehicleNo; }
  public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
  public BigDecimal getLatitude() { return latitude; }
  public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
  public BigDecimal getLongitude() { return longitude; }
  public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
  public Instant getRecordedAt() { return recordedAt; }
  public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
