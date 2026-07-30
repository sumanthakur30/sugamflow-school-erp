package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tax_rule")
public class TaxRuleEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "country_code", nullable = false, length = 8)
  private String countryCode = "IN";

  @Column(name = "hsn_sac", length = 32)
  private String hsnSac;

  @Column(name = "cgst_bps", nullable = false)
  private int cgstBps;

  @Column(name = "sgst_bps", nullable = false)
  private int sgstBps;

  @Column(name = "igst_bps", nullable = false)
  private int igstBps;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "is_default", nullable = false)
  private boolean defaultRule = false;

  @Column(name = "effective_from")
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

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

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getHsnSac() {
    return hsnSac;
  }

  public void setHsnSac(String hsnSac) {
    this.hsnSac = hsnSac;
  }

  public int getCgstBps() {
    return cgstBps;
  }

  public void setCgstBps(int cgstBps) {
    this.cgstBps = cgstBps;
  }

  public int getSgstBps() {
    return sgstBps;
  }

  public void setSgstBps(int sgstBps) {
    this.sgstBps = sgstBps;
  }

  public int getIgstBps() {
    return igstBps;
  }

  public void setIgstBps(int igstBps) {
    this.igstBps = igstBps;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public boolean isDefaultRule() {
    return defaultRule;
  }

  public void setDefaultRule(boolean defaultRule) {
    this.defaultRule = defaultRule;
  }

  public Instant getEffectiveFrom() {
    return effectiveFrom;
  }

  public void setEffectiveFrom(Instant effectiveFrom) {
    this.effectiveFrom = effectiveFrom;
  }

  public Instant getEffectiveTo() {
    return effectiveTo;
  }

  public void setEffectiveTo(Instant effectiveTo) {
    this.effectiveTo = effectiveTo;
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
