package com.sugamflow.school.cms.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cms_news")
public class CmsNews {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "site_id")
  private UUID siteId;

  @Column(nullable = false, length = 64)
  private String category = "NOTICE";

  @Column(nullable = false)
  private int priority;

  @Column(nullable = false, length = 32)
  private String audience = "PUBLIC";

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(nullable = false, length = 128)
  private String slug;

  @Column(nullable = false, length = 256)
  private String title;

  @Column(length = 1024)
  private String summary;

  @Column(name = "body_html", nullable = false, columnDefinition = "text")
  private String bodyHtml;

  @Column(name = "cover_image_url", length = 1024)
  private String coverImageUrl;

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
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public int getPriority() { return priority; }
  public void setPriority(int priority) { this.priority = priority; }
  public String getAudience() { return audience; }
  public void setAudience(String audience) { this.audience = audience; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getSummary() { return summary; }
  public void setSummary(String summary) { this.summary = summary; }
  public String getBodyHtml() { return bodyHtml; }
  public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
  public String getCoverImageUrl() { return coverImageUrl; }
  public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getPublishedAt() { return publishedAt; }
  public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
