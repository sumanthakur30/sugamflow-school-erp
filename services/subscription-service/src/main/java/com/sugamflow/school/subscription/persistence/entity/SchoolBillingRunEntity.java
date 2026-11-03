package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "school_billing_run")
public class SchoolBillingRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "period_key", nullable = false, length = 16)
  private String periodKey;

  @Column(name = "obligation_type", nullable = false, length = 32)
  private String obligationType = "SCHOOL_PER_STUDENT";

  @Column(name = "count_policy", nullable = false, length = 32)
  private String countPolicy;

  @Column(name = "billable_count", nullable = false)
  private int billableCount;

  @Column(name = "snapshot_at", nullable = false)
  private Instant snapshotAt;

  @Column(name = "rate_version_id")
  private Long rateVersionId;

  @Column(name = "unit_amount_minor", nullable = false)
  private long unitAmountMinor;

  @Column(name = "base_fee_minor", nullable = false)
  private long baseFeeMinor;

  @Column(name = "discount_minor", nullable = false)
  private long discountMinor;

  @Column(name = "taxable_minor", nullable = false)
  private long taxableMinor;

  @Column(name = "cgst_minor", nullable = false)
  private long cgstMinor;

  @Column(name = "sgst_minor", nullable = false)
  private long sgstMinor;

  @Column(name = "igst_minor", nullable = false)
  private long igstMinor;

  @Column(name = "tax_minor", nullable = false)
  private long taxMinor;

  @Column(name = "total_minor", nullable = false)
  private long totalMinor;

  @Column(name = "invoice_id")
  private Long invoiceId;

  @Column(name = "job_status", nullable = false, length = 16)
  private String jobStatus;

  @Column(name = "error_message", length = 512)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

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

  public String getPeriodKey() {
    return periodKey;
  }

  public void setPeriodKey(String periodKey) {
    this.periodKey = periodKey;
  }

  public String getObligationType() {
    return obligationType;
  }

  public void setObligationType(String obligationType) {
    this.obligationType = obligationType;
  }

  public String getCountPolicy() {
    return countPolicy;
  }

  public void setCountPolicy(String countPolicy) {
    this.countPolicy = countPolicy;
  }

  public int getBillableCount() {
    return billableCount;
  }

  public void setBillableCount(int billableCount) {
    this.billableCount = billableCount;
  }

  public Instant getSnapshotAt() {
    return snapshotAt;
  }

  public void setSnapshotAt(Instant snapshotAt) {
    this.snapshotAt = snapshotAt;
  }

  public Long getRateVersionId() {
    return rateVersionId;
  }

  public void setRateVersionId(Long rateVersionId) {
    this.rateVersionId = rateVersionId;
  }

  public long getUnitAmountMinor() {
    return unitAmountMinor;
  }

  public void setUnitAmountMinor(long unitAmountMinor) {
    this.unitAmountMinor = unitAmountMinor;
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

  public long getTaxableMinor() {
    return taxableMinor;
  }

  public void setTaxableMinor(long taxableMinor) {
    this.taxableMinor = taxableMinor;
  }

  public long getCgstMinor() {
    return cgstMinor;
  }

  public void setCgstMinor(long cgstMinor) {
    this.cgstMinor = cgstMinor;
  }

  public long getSgstMinor() {
    return sgstMinor;
  }

  public void setSgstMinor(long sgstMinor) {
    this.sgstMinor = sgstMinor;
  }

  public long getIgstMinor() {
    return igstMinor;
  }

  public void setIgstMinor(long igstMinor) {
    this.igstMinor = igstMinor;
  }

  public long getTaxMinor() {
    return taxMinor;
  }

  public void setTaxMinor(long taxMinor) {
    this.taxMinor = taxMinor;
  }

  public long getTotalMinor() {
    return totalMinor;
  }

  public void setTotalMinor(long totalMinor) {
    this.totalMinor = totalMinor;
  }

  public Long getInvoiceId() {
    return invoiceId;
  }

  public void setInvoiceId(Long invoiceId) {
    this.invoiceId = invoiceId;
  }

  public String getJobStatus() {
    return jobStatus;
  }

  public void setJobStatus(String jobStatus) {
    this.jobStatus = jobStatus;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
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
