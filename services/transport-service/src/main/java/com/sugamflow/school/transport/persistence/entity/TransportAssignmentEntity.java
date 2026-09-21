package com.sugamflow.school.transport.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transport_assignment")
public class TransportAssignmentEntity {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "route_id", nullable = false)
  private UUID routeId;

  @Column(name = "student_id")
  private UUID studentId;

  @Column(name = "admission_no", nullable = false, length = 64)
  private String admissionNo;

  @Column(name = "student_name", length = 191)
  private String studentName;

  @Column(name = "stop_name", length = 191)
  private String stopName;

  @Column(name = "pickup_time", length = 32)
  private String pickupTime;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt = Instant.now();

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "created_by", length = 128)
  private String createdBy;

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
  public UUID getRouteId() { return routeId; }
  public void setRouteId(UUID routeId) { this.routeId = routeId; }
  public UUID getStudentId() { return studentId; }
  public void setStudentId(UUID studentId) { this.studentId = studentId; }
  public String getAdmissionNo() { return admissionNo; }
  public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
  public String getStudentName() { return studentName; }
  public void setStudentName(String studentName) { this.studentName = studentName; }
  public String getStopName() { return stopName; }
  public void setStopName(String stopName) { this.stopName = stopName; }
  public String getPickupTime() { return pickupTime; }
  public void setPickupTime(String pickupTime) { this.pickupTime = pickupTime; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getAssignedAt() { return assignedAt; }
  public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
  public Instant getEndedAt() { return endedAt; }
  public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
