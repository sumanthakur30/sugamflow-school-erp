package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "credit_policy")
public class CreditPolicyEntity {

  @Id
  @Column(name = "meter_code", length = 64)
  private String meterCode;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "period_type", nullable = false, length = 16)
  private String periodType = "MONTHLY";

  @Column(name = "carry_forward_bps", nullable = false)
  private int carryForwardBps;

  @Column(name = "carry_forward_cap")
  private Long carryForwardCap;

  @Column(name = "expire_unused", nullable = false)
  private boolean expireUnused;

  @Column(name = "plan_grant_amount", nullable = false)
  private long planGrantAmount;

  @Column(nullable = false)
  private boolean active = true;

  @Column(length = 512)
  private String notes;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getMeterCode() {
    return meterCode;
  }

  public void setMeterCode(String meterCode) {
    this.meterCode = meterCode;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPeriodType() {
    return periodType;
  }

  public void setPeriodType(String periodType) {
    this.periodType = periodType;
  }

  public int getCarryForwardBps() {
    return carryForwardBps;
  }

  public void setCarryForwardBps(int carryForwardBps) {
    this.carryForwardBps = carryForwardBps;
  }

  public Long getCarryForwardCap() {
    return carryForwardCap;
  }

  public void setCarryForwardCap(Long carryForwardCap) {
    this.carryForwardCap = carryForwardCap;
  }

  public boolean isExpireUnused() {
    return expireUnused;
  }

  public void setExpireUnused(boolean expireUnused) {
    this.expireUnused = expireUnused;
  }

  public long getPlanGrantAmount() {
    return planGrantAmount;
  }

  public void setPlanGrantAmount(long planGrantAmount) {
    this.planGrantAmount = planGrantAmount;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
