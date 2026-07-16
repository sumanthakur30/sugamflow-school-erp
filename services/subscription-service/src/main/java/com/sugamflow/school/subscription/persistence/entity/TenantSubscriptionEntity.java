package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_subscription")
public class TenantSubscriptionEntity {

  @Id
  @Column(name = "organization_id", length = 64)
  private String organizationId;

  @Column(name = "plan_id", nullable = false, length = 64)
  private String planId;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt = Instant.now();

  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getPlanId() { return planId; }
  public void setPlanId(String planId) { this.planId = planId; }
  public Instant getAssignedAt() { return assignedAt; }
  public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
}
