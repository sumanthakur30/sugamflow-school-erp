package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "usage_event")
public class UsageEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "limit_code", nullable = false, length = 64)
  private String limitCode;

  @Column(name = "period_key", nullable = false, length = 32)
  private String periodKey = "ALL";

  @Column(nullable = false)
  private long delta;

  @Column(name = "used_after", nullable = false)
  private long usedAfter;

  @Column(length = 256)
  private String reason;

  @Column(length = 128)
  private String actor;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getLimitCode() {
    return limitCode;
  }

  public void setLimitCode(String limitCode) {
    this.limitCode = limitCode;
  }

  public String getPeriodKey() {
    return periodKey;
  }

  public void setPeriodKey(String periodKey) {
    this.periodKey = periodKey;
  }

  public long getDelta() {
    return delta;
  }

  public void setDelta(long delta) {
    this.delta = delta;
  }

  public long getUsedAfter() {
    return usedAfter;
  }

  public void setUsedAfter(long usedAfter) {
    this.usedAfter = usedAfter;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
