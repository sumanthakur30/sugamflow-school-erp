package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_subscription_lifecycle")
public class TenantSubscriptionLifecycleEntity {

  @Id
  @Column(name = "organization_id", length = 64)
  private String organizationId;

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @Column(name = "trial_ends_at")
  private Instant trialEndsAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "grace_ends_at")
  private Instant graceEndsAt;

  @Column(name = "grace_days", nullable = false)
  private int graceDays = 7;

  @Column(name = "enforcement_enabled", nullable = false)
  private boolean enforcementEnabled = false;

  @Column(length = 512)
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getTrialEndsAt() {
    return trialEndsAt;
  }

  public void setTrialEndsAt(Instant trialEndsAt) {
    this.trialEndsAt = trialEndsAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getGraceEndsAt() {
    return graceEndsAt;
  }

  public void setGraceEndsAt(Instant graceEndsAt) {
    this.graceEndsAt = graceEndsAt;
  }

  public int getGraceDays() {
    return graceDays;
  }

  public void setGraceDays(int graceDays) {
    this.graceDays = graceDays;
  }

  public boolean isEnforcementEnabled() {
    return enforcementEnabled;
  }

  public void setEnforcementEnabled(boolean enforcementEnabled) {
    this.enforcementEnabled = enforcementEnabled;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
