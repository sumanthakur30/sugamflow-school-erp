package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "subscription_invoice")
public class SubscriptionInvoiceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "invoice_number", length = 64)
  private String invoiceNumber;

  @Column(nullable = false, length = 32)
  private String status = "DRAFT";

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(name = "plan_id", length = 64)
  private String planId;

  @Column(name = "billing_cycle_code", length = 32)
  private String billingCycleCode;

  @Column(name = "price_book_id", length = 64)
  private String priceBookId;

  @Column(name = "subtotal_minor", nullable = false)
  private long subtotalMinor;

  @Column(name = "tax_minor", nullable = false)
  private long taxMinor;

  @Column(name = "total_minor", nullable = false)
  private long totalMinor;

  @Column(name = "tax_rule_id", length = 64)
  private String taxRuleId;

  @Column(name = "cgst_minor", nullable = false)
  private long cgstMinor;

  @Column(name = "sgst_minor", nullable = false)
  private long sgstMinor;

  @Column(name = "igst_minor", nullable = false)
  private long igstMinor;

  @Column(name = "place_of_supply", length = 8)
  private String placeOfSupply;

  @Column(name = "seller_state_code", length = 8)
  private String sellerStateCode;

  @Column(name = "discount_minor", nullable = false)
  private long discountMinor;

  @Column(name = "coupon_code", length = 64)
  private String couponCode;

  @Column(name = "proration_factor")
  private java.math.BigDecimal prorationFactor;

  @Column(name = "gateway_order_id", length = 128)
  private String gatewayOrderId;

  @Column(name = "period_start")
  private Instant periodStart;

  @Column(name = "period_end")
  private Instant periodEnd;

  @Column(name = "issued_at")
  private Instant issuedAt;

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(length = 512)
  private String notes;

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

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
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

  public String getPriceBookId() {
    return priceBookId;
  }

  public void setPriceBookId(String priceBookId) {
    this.priceBookId = priceBookId;
  }

  public long getSubtotalMinor() {
    return subtotalMinor;
  }

  public void setSubtotalMinor(long subtotalMinor) {
    this.subtotalMinor = subtotalMinor;
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

  public String getTaxRuleId() {
    return taxRuleId;
  }

  public void setTaxRuleId(String taxRuleId) {
    this.taxRuleId = taxRuleId;
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

  public String getPlaceOfSupply() {
    return placeOfSupply;
  }

  public void setPlaceOfSupply(String placeOfSupply) {
    this.placeOfSupply = placeOfSupply;
  }

  public String getSellerStateCode() {
    return sellerStateCode;
  }

  public void setSellerStateCode(String sellerStateCode) {
    this.sellerStateCode = sellerStateCode;
  }

  public long getDiscountMinor() {
    return discountMinor;
  }

  public void setDiscountMinor(long discountMinor) {
    this.discountMinor = discountMinor;
  }

  public String getCouponCode() {
    return couponCode;
  }

  public void setCouponCode(String couponCode) {
    this.couponCode = couponCode;
  }

  public java.math.BigDecimal getProrationFactor() {
    return prorationFactor;
  }

  public void setProrationFactor(java.math.BigDecimal prorationFactor) {
    this.prorationFactor = prorationFactor;
  }

  public String getGatewayOrderId() {
    return gatewayOrderId;
  }

  public void setGatewayOrderId(String gatewayOrderId) {
    this.gatewayOrderId = gatewayOrderId;
  }

  public Instant getPeriodStart() {
    return periodStart;
  }

  public void setPeriodStart(Instant periodStart) {
    this.periodStart = periodStart;
  }

  public Instant getPeriodEnd() {
    return periodEnd;
  }

  public void setPeriodEnd(Instant periodEnd) {
    this.periodEnd = periodEnd;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public void setIssuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public void setDueAt(Instant dueAt) {
    this.dueAt = dueAt;
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
