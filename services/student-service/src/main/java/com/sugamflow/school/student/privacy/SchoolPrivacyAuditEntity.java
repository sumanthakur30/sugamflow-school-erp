package com.sugamflow.school.student.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_privacy_audit")
public class SchoolPrivacyAuditEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(nullable = false, length = 128)
  private String actor;

  @Column(nullable = false, length = 40)
  private String action;

  @Column(name = "target_ref", length = 80)
  private String targetRef;

  @Column(length = 500)
  private String detail;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getActor() { return actor; }
  public void setActor(String actor) { this.actor = actor; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getTargetRef() { return targetRef; }
  public void setTargetRef(String targetRef) { this.targetRef = targetRef; }
  public String getDetail() { return detail; }
  public void setDetail(String detail) { this.detail = detail; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
