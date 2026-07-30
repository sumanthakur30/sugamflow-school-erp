package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "credit_ledger")
public class CreditLedgerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "meter_code", nullable = false, length = 64)
  private String meterCode;

  @Column(nullable = false)
  private long delta;

  @Column(name = "balance_after", nullable = false)
  private long balanceAfter;

  @Column(length = 256)
  private String reason;

  @Column(name = "invoice_id")
  private Long invoiceId;

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

  public long getDelta() {
    return delta;
  }

  public void setDelta(long delta) {
    this.delta = delta;
  }

  public long getBalanceAfter() {
    return balanceAfter;
  }

  public void setBalanceAfter(long balanceAfter) {
    this.balanceAfter = balanceAfter;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Long getInvoiceId() {
    return invoiceId;
  }

  public void setInvoiceId(Long invoiceId) {
    this.invoiceId = invoiceId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
