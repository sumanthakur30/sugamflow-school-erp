package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "price_book")
public class PriceBookEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "is_default", nullable = false)
  private boolean defaultBook = false;

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

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public boolean isDefaultBook() {
    return defaultBook;
  }

  public void setDefaultBook(boolean defaultBook) {
    this.defaultBook = defaultBook;
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
