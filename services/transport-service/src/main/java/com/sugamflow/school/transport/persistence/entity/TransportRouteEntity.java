package com.sugamflow.school.transport.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transport_route")
public class TransportRouteEntity {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "route_key", nullable = false, length = 64)
  private String routeKey;

  @Column(name = "route_name", nullable = false, length = 191)
  private String routeName;

  @Column(name = "vehicle_no", length = 64)
  private String vehicleNo;

  @Column(nullable = false)
  private int capacity = 40;

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getRouteKey() { return routeKey; }
  public void setRouteKey(String routeKey) { this.routeKey = routeKey; }
  public String getRouteName() { return routeName; }
  public void setRouteName(String routeName) { this.routeName = routeName; }
  public String getVehicleNo() { return vehicleNo; }
  public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
  public int getCapacity() { return capacity; }
  public void setCapacity(int capacity) { this.capacity = capacity; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
