package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "limit_definition")
public class LimitDefinitionEntity {

  @Id
  @Column(length = 64)
  private String code;

  @Column(name = "business_type_code", length = 64)
  private String businessTypeCode;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 64)
  private String unit = "COUNT";

  @Column(nullable = false, length = 32)
  private String aggregation = "NUMERIC";

  @Column(length = 512)
  private String description;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getBusinessTypeCode() {
    return businessTypeCode;
  }

  public void setBusinessTypeCode(String businessTypeCode) {
    this.businessTypeCode = businessTypeCode;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public String getAggregation() {
    return aggregation;
  }

  public void setAggregation(String aggregation) {
    this.aggregation = aggregation;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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
}
