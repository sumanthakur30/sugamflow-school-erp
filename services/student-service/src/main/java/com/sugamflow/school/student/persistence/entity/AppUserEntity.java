package com.sugamflow.school.student.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUserEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "role_code", nullable = false, length = 32)
  private String roleCode;

  @Column(name = "subject_ref", length = 128)
  private String subjectRef;

  @Column(name = "display_name", length = 191)
  private String displayName;

  @Column(length = 128)
  private String username;

  @Column(nullable = false)
  private boolean installed;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getRoleCode() { return roleCode; }
  public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
  public String getSubjectRef() { return subjectRef; }
  public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public boolean isInstalled() { return installed; }
  public void setInstalled(boolean installed) { this.installed = installed; }
  public Instant getLastSeenAt() { return lastSeenAt; }
  public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
