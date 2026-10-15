package com.sugamflow.school.attendance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "biometric_audit")
public class BiometricAuditEntity {
  @Id private UUID id;
  @Column(name = "organization_id", nullable = false, length = 64) private String organizationId;
  @Column(name = "user_id", length = 128) private String userId;
  @Column(nullable = false, length = 64) private String action;
  @Column(name = "entity_name", nullable = false, length = 64) private String entityName;
  @Column(name = "entity_id", length = 64) private String entityId;
  @Column(name = "old_value", length = 1024) private String oldValue;
  @Column(name = "new_value", length = 1024) private String newValue;
  @Column(name = "ip_address", length = 64) private String ipAddress;
  @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getEntityName() { return entityName; }
  public void setEntityName(String entityName) { this.entityName = entityName; }
  public String getEntityId() { return entityId; }
  public void setEntityId(String entityId) { this.entityId = entityId; }
  public String getOldValue() { return oldValue; }
  public void setOldValue(String oldValue) { this.oldValue = oldValue; }
  public String getNewValue() { return newValue; }
  public void setNewValue(String newValue) { this.newValue = newValue; }
  public String getIpAddress() { return ipAddress; }
  public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
