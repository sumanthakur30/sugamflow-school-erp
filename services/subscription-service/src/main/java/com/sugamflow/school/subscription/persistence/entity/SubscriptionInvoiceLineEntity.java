package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription_invoice_line")
public class SubscriptionInvoiceLineEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "invoice_id", nullable = false)
  private Long invoiceId;

  @Column(name = "line_type", nullable = false, length = 32)
  private String lineType = "PLAN";

  @Column(nullable = false, length = 256)
  private String description;

  @Column(nullable = false)
  private int quantity = 1;

  @Column(name = "unit_amount_minor", nullable = false)
  private long unitAmountMinor;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(name = "plan_id", length = 64)
  private String planId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getInvoiceId() {
    return invoiceId;
  }

  public void setInvoiceId(Long invoiceId) {
    this.invoiceId = invoiceId;
  }

  public String getLineType() {
    return lineType;
  }

  public void setLineType(String lineType) {
    this.lineType = lineType;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public long getUnitAmountMinor() {
    return unitAmountMinor;
  }

  public void setUnitAmountMinor(long unitAmountMinor) {
    this.unitAmountMinor = unitAmountMinor;
  }

  public long getAmountMinor() {
    return amountMinor;
  }

  public void setAmountMinor(long amountMinor) {
    this.amountMinor = amountMinor;
  }

  public String getPlanId() {
    return planId;
  }

  public void setPlanId(String planId) {
    this.planId = planId;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
