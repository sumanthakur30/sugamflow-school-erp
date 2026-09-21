package com.sugamflow.school.library.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "library_book")
public class LibraryBookEntity {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(length = 64)
  private String isbn;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(length = 191)
  private String author;

  @Column(name = "category_key", length = 64)
  private String categoryKey;

  @Column(name = "copies_total", nullable = false)
  private int copiesTotal = 1;

  @Column(name = "copies_available", nullable = false)
  private int copiesAvailable = 1;

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getIsbn() { return isbn; }
  public void setIsbn(String isbn) { this.isbn = isbn; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getAuthor() { return author; }
  public void setAuthor(String author) { this.author = author; }
  public String getCategoryKey() { return categoryKey; }
  public void setCategoryKey(String categoryKey) { this.categoryKey = categoryKey; }
  public int getCopiesTotal() { return copiesTotal; }
  public void setCopiesTotal(int copiesTotal) { this.copiesTotal = copiesTotal; }
  public int getCopiesAvailable() { return copiesAvailable; }
  public void setCopiesAvailable(int copiesAvailable) { this.copiesAvailable = copiesAvailable; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
