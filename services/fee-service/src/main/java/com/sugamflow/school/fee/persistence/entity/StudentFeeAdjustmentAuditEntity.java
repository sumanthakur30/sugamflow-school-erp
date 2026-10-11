package com.sugamflow.school.fee.persistence.entity;

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
@Table(name = "student_fee_adjustment_audit")
public class StudentFeeAdjustmentAuditEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "adjustment_id", nullable = false, length = 64)
  private String adjustmentId;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(nullable = false, length = 32)
  private String action;

  @Column(length = 128)
  private String actor;

  @Column(length = 500)
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> snapshot = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getAdjustmentId() { return adjustmentId; }
  public void setAdjustmentId(String adjustmentId) { this.adjustmentId = adjustmentId; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getActor() { return actor; }
  public void setActor(String actor) { this.actor = actor; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public Map<String, Object> getSnapshot() { return snapshot; }
  public void setSnapshot(Map<String, Object> snapshot) { this.snapshot = snapshot; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
