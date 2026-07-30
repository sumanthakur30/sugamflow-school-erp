package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "credit_period_run")
public class CreditPeriodRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "meter_code", nullable = false, length = 64)
  private String meterCode;

  @Column(name = "period_key", nullable = false, length = 32)
  private String periodKey;

  @Column(name = "opening_balance", nullable = false)
  private long openingBalance;

  @Column(name = "carried_forward", nullable = false)
  private long carriedForward;

  @Column(nullable = false)
  private long expired;

  @Column(nullable = false)
  private long granted;

  @Column(name = "closing_balance", nullable = false)
  private long closingBalance;

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

  public String getMeterCode() {
    return meterCode;
  }

  public void setMeterCode(String meterCode) {
    this.meterCode = meterCode;
  }

  public String getPeriodKey() {
    return periodKey;
  }

  public void setPeriodKey(String periodKey) {
    this.periodKey = periodKey;
  }

  public long getOpeningBalance() {
    return openingBalance;
  }

  public void setOpeningBalance(long openingBalance) {
    this.openingBalance = openingBalance;
  }

  public long getCarriedForward() {
    return carriedForward;
  }

  public void setCarriedForward(long carriedForward) {
    this.carriedForward = carriedForward;
  }

  public long getExpired() {
    return expired;
  }

  public void setExpired(long expired) {
    this.expired = expired;
  }

  public long getGranted() {
    return granted;
  }

  public void setGranted(long granted) {
    this.granted = granted;
  }

  public long getClosingBalance() {
    return closingBalance;
  }

  public void setClosingBalance(long closingBalance) {
    this.closingBalance = closingBalance;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
