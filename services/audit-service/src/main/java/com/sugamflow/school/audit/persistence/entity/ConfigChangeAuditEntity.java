package com.sugamflow.school.audit.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "config_change_audit")
public class ConfigChangeAuditEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "entity_type", length = 128)
  private String entityType;

  @Column(name = "entity_key", length = 128)
  private String entityKey;

  @Column(nullable = false, length = 64)
  private String status;

  @Column(name = "changed_by", length = 128)
  private String changedBy;

  @Column(columnDefinition = "text")
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "old_value", columnDefinition = "jsonb")
  private Map<String, Object> oldValue;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "new_value", columnDefinition = "jsonb")
  private Map<String, Object> newValue;

  @Column(name = "rollback_of", length = 64)
  private String rollbackOf;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getEntityType() { return entityType; }
  public void setEntityType(String entityType) { this.entityType = entityType; }
  public String getEntityKey() { return entityKey; }
  public void setEntityKey(String entityKey) { this.entityKey = entityKey; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getChangedBy() { return changedBy; }
  public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Map<String, Object> getOldValue() { return oldValue; }
  public void setOldValue(Map<String, Object> oldValue) { this.oldValue = oldValue; }
  public Map<String, Object> getNewValue() { return newValue; }
  public void setNewValue(Map<String, Object> newValue) { this.newValue = newValue; }
  public String getRollbackOf() { return rollbackOf; }
  public void setRollbackOf(String rollbackOf) { this.rollbackOf = rollbackOf; }
  public Instant getApprovedAt() { return approvedAt; }
  public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
