package com.sugamflow.school.cms.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cms_alumni_profile")
public class CmsAlumniProfile {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(nullable = false, length = 128)
  private String slug;

  @Column(name = "full_name", nullable = false, length = 256)
  private String fullName;

  @Column(name = "batch_year")
  private Integer batchYear;

  @Column(length = 512)
  private String headline;

  @Column(name = "bio_html", nullable = false, columnDefinition = "text")
  private String bioHtml;

  @Column(name = "photo_url", length = 1024)
  private String photoUrl;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }
  public Integer getBatchYear() { return batchYear; }
  public void setBatchYear(Integer batchYear) { this.batchYear = batchYear; }
  public String getHeadline() { return headline; }
  public void setHeadline(String headline) { this.headline = headline; }
  public String getBioHtml() { return bioHtml; }
  public void setBioHtml(String bioHtml) { this.bioHtml = bioHtml; }
  public String getPhotoUrl() { return photoUrl; }
  public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getPublishedAt() { return publishedAt; }
  public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
