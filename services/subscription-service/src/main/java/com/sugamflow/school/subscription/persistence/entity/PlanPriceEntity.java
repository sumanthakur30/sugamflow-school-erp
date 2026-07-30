package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "plan_price")
public class PlanPriceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "price_book_id", nullable = false, length = 64)
  private String priceBookId;

  @Column(name = "plan_id", nullable = false, length = 64)
  private String planId;

  @Column(name = "billing_cycle_code", nullable = false, length = 32)
  private String billingCycleCode;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getPriceBookId() {
    return priceBookId;
  }

  public void setPriceBookId(String priceBookId) {
    this.priceBookId = priceBookId;
  }

  public String getPlanId() {
    return planId;
  }

  public void setPlanId(String planId) {
    this.planId = planId;
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

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
