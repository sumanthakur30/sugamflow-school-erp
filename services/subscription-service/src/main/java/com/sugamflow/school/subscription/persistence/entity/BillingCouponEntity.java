package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "billing_coupon")
public class BillingCouponEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "discount_type", nullable = false, length = 16)
  private String discountType;

  @Column(name = "discount_value", nullable = false)
  private long discountValue;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(name = "max_redemptions")
  private Integer maxRedemptions;

  @Column(name = "redemption_count", nullable = false)
  private int redemptionCount;

  @Column(name = "min_subtotal_minor", nullable = false)
  private long minSubtotalMinor;

  @Column(name = "applicable_plan_id", length = 64)
  private String applicablePlanId;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_to")
  private Instant validTo;

  @Column(length = 512)
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDiscountType() {
    return discountType;
  }

  public void setDiscountType(String discountType) {
    this.discountType = discountType;
  }

  public long getDiscountValue() {
    return discountValue;
  }

  public void setDiscountValue(long discountValue) {
    this.discountValue = discountValue;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Integer getMaxRedemptions() {
    return maxRedemptions;
  }

  public void setMaxRedemptions(Integer maxRedemptions) {
    this.maxRedemptions = maxRedemptions;
  }

  public int getRedemptionCount() {
    return redemptionCount;
  }

  public void setRedemptionCount(int redemptionCount) {
    this.redemptionCount = redemptionCount;
  }

  public long getMinSubtotalMinor() {
    return minSubtotalMinor;
  }

  public void setMinSubtotalMinor(long minSubtotalMinor) {
    this.minSubtotalMinor = minSubtotalMinor;
  }

  public String getApplicablePlanId() {
    return applicablePlanId;
  }

  public void setApplicablePlanId(String applicablePlanId) {
    this.applicablePlanId = applicablePlanId;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(Instant validFrom) {
    this.validFrom = validFrom;
  }

  public Instant getValidTo() {
    return validTo;
  }

  public void setValidTo(Instant validTo) {
    this.validTo = validTo;
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
