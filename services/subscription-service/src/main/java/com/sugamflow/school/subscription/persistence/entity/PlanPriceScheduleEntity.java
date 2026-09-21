package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "plan_price_schedule")
public class PlanPriceScheduleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plan_id", nullable = false, length = 64)
  private String planId;

  @Column(name = "price_book_id", nullable = false, length = 64)
  private String priceBookId;

  @Column(name = "billing_cycle_code", nullable = false, length = 32)
  private String billingCycleCode;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(name = "effective_at", nullable = false)
  private Instant effectiveAt;

  @Column(nullable = false, length = 16)
  private String status = "SCHEDULED";

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(length = 512)
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getPlanId() {
    return planId;
  }

  public void setPlanId(String planId) {
    this.planId = planId;
  }

  public String getPriceBookId() {
    return priceBookId;
  }

  public void setPriceBookId(String priceBookId) {
    this.priceBookId = priceBookId;
  }

  public String getBillingCycleCode() {
    return billingCycleCode;
  }

  public void setBillingCycleCode(String billingCycleCode) {
    this.billingCycleCode = billingCycleCode;
  }

  public long getAmountMinor() {
    return amountMinor;
  }

  public void setAmountMinor(long amountMinor) {
    this.amountMinor = amountMinor;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Instant getEffectiveAt() {
    return effectiveAt;
  }

  public void setEffectiveAt(Instant effectiveAt) {
    this.effectiveAt = effectiveAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }

  public void setAppliedAt(Instant appliedAt) {
    this.appliedAt = appliedAt;
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
}
