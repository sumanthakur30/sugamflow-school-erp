package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "school_billing_contract")
public class SchoolBillingContractEntity {

  @Id
  @Column(name = "organization_id", length = 64)
  private String organizationId;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "unit_model", nullable = false, length = 40)
  private String unitModel = "PER_STUDENT";

  @Column(name = "per_student_rate_minor")
  private Long perStudentRateMinor;

  @Column(name = "base_fee_minor", nullable = false)
  private long baseFeeMinor;

  @Column(name = "discount_minor", nullable = false)
  private long discountMinor;

  @Column(name = "buyer_state_code", length = 8)
  private String buyerStateCode;

  @Column(name = "seller_state_code", length = 8)
  private String sellerStateCode;

  @Column(name = "count_policy", nullable = false, length = 32)
  private String countPolicy = "ACTIVE_ON_RUN";

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom = Instant.now();

  @Column(name = "effective_until")
  private Instant effectiveUntil;

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

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public String getUnitModel() {
    return unitModel;
  }

  public void setUnitModel(String unitModel) {
    this.unitModel = unitModel;
  }

  public Long getPerStudentRateMinor() {
    return perStudentRateMinor;
  }

  public void setPerStudentRateMinor(Long perStudentRateMinor) {
    this.perStudentRateMinor = perStudentRateMinor;
  }

  public long getBaseFeeMinor() {
    return baseFeeMinor;
  }

  public void setBaseFeeMinor(long baseFeeMinor) {
    this.baseFeeMinor = baseFeeMinor;
  }

  public long getDiscountMinor() {
    return discountMinor;
  }

  public void setDiscountMinor(long discountMinor) {
    this.discountMinor = discountMinor;
  }

  public String getBuyerStateCode() {
    return buyerStateCode;
  }

  public void setBuyerStateCode(String buyerStateCode) {
    this.buyerStateCode = buyerStateCode;
  }

  public String getSellerStateCode() {
    return sellerStateCode;
  }

  public void setSellerStateCode(String sellerStateCode) {
    this.sellerStateCode = sellerStateCode;
  }

  public String getCountPolicy() {
    return countPolicy;
  }

  public void setCountPolicy(String countPolicy) {
    this.countPolicy = countPolicy;
  }

  public Instant getEffectiveFrom() {
    return effectiveFrom;
  }

  public void setEffectiveFrom(Instant effectiveFrom) {
    this.effectiveFrom = effectiveFrom;
  }

  public Instant getEffectiveUntil() {
    return effectiveUntil;
  }

  public void setEffectiveUntil(Instant effectiveUntil) {
    this.effectiveUntil = effectiveUntil;
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
