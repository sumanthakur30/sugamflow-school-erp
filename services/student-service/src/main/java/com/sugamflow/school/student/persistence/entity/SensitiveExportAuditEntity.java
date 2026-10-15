package com.sugamflow.school.student.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensitive_export_audit")
public class SensitiveExportAuditEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "export_kind", nullable = false, length = 64)
  private String exportKind;

  @Column(name = "includes_aadhaar", nullable = false)
  private boolean includesAadhaar;

  @Column(name = "row_count", nullable = false)
  private int rowCount;

  @Column(name = "actor_id", length = 128)
  private String actorId;

  @Column(name = "actor_role", length = 64)
  private String actorRole;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getBranchId() {
    return branchId;
  }

  public void setBranchId(String branchId) {
    this.branchId = branchId;
  }

  public String getExportKind() {
    return exportKind;
  }

  public void setExportKind(String exportKind) {
    this.exportKind = exportKind;
  }

  public boolean isIncludesAadhaar() {
    return includesAadhaar;
  }

  public void setIncludesAadhaar(boolean includesAadhaar) {
    this.includesAadhaar = includesAadhaar;
  }

  public int getRowCount() {
    return rowCount;
  }

  public void setRowCount(int rowCount) {
    this.rowCount = rowCount;
  }

  public String getActorId() {
    return actorId;
  }

  public void setActorId(String actorId) {
    this.actorId = actorId;
  }

  public String getActorRole() {
    return actorRole;
  }

  public void setActorRole(String actorRole) {
    this.actorRole = actorRole;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
