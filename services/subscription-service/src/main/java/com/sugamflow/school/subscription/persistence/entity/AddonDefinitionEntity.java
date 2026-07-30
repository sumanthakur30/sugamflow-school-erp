package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "addon_definition")
public class AddonDefinitionEntity {

  @Id
  @Column(length = 64)
  private String sku;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(length = 512)
  private String description;

  @Column(name = "addon_type", nullable = false, length = 32)
  private String addonType;

  @Column(name = "meter_code", length = 64)
  private String meterCode;

  @Column(name = "credit_amount", nullable = false)
  private long creditAmount;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getAddonType() {
    return addonType;
  }

  public void setAddonType(String addonType) {
    this.addonType = addonType;
  }

  public String getMeterCode() {
    return meterCode;
  }

  public void setMeterCode(String meterCode) {
    this.meterCode = meterCode;
  }

  public long getCreditAmount() {
    return creditAmount;
  }

  public void setCreditAmount(long creditAmount) {
    this.creditAmount = creditAmount;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
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
