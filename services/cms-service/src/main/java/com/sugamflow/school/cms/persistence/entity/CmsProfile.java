package com.sugamflow.school.cms.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cms_profile")
public class CmsProfile {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "site_id")
  private UUID siteId;

  @Column(name = "full_name", nullable = false, length = 256)
  private String fullName;

  @Column(length = 128)
  private String role;

  @Column(name = "photo_url", length = 1024)
  private String photoUrl;

  @Column(name = "media_asset_id")
  private UUID mediaAssetId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

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
  public UUID getSiteId() { return siteId; }
  public void setSiteId(UUID siteId) { this.siteId = siteId; }
  public String getFullName() { return fullName; }
  public void setFullName(String fullName) { this.fullName = fullName; }
  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }
  public String getPhotoUrl() { return photoUrl; }
  public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
  public UUID getMediaAssetId() { return mediaAssetId; }
  public void setMediaAssetId(UUID mediaAssetId) { this.mediaAssetId = mediaAssetId; }
  public int getSortOrder() { return sortOrder; }
  public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getPublishedAt() { return publishedAt; }
  public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
