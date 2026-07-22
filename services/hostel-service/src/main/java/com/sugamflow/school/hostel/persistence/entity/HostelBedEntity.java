package com.sugamflow.school.hostel.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hostel_bed")
public class HostelBedEntity {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "block_key", nullable = false, length = 64)
  private String blockKey;

  @Column(name = "room_no", nullable = false, length = 32)
  private String roomNo;

  @Column(name = "bed_no", nullable = false)
  private int bedNo;

  @Column(nullable = false, length = 32)
  private String status = "VACANT";

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
  public String getBlockKey() { return blockKey; }
  public void setBlockKey(String blockKey) { this.blockKey = blockKey; }
  public String getRoomNo() { return roomNo; }
  public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
  public int getBedNo() { return bedNo; }
  public void setBedNo(int bedNo) { this.bedNo = bedNo; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
