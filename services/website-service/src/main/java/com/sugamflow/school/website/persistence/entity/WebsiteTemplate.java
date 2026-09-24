package com.sugamflow.school.website.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "website_template")
public class WebsiteTemplate {

  @Id
  @Column(length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(length = 1024)
  private String description;

  @Column(name = "preview_image_url", length = 1024)
  private String previewImageUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "theme_json", nullable = false, columnDefinition = "jsonb")
  private String themeJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "homepage_json", nullable = false, columnDefinition = "jsonb")
  private String homepageJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "navigation_json", nullable = false, columnDefinition = "jsonb")
  private String navigationJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "seo_json", nullable = false, columnDefinition = "jsonb")
  private String seoJson;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 100;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getPreviewImageUrl() { return previewImageUrl; }
  public void setPreviewImageUrl(String previewImageUrl) { this.previewImageUrl = previewImageUrl; }
  public String getThemeJson() { return themeJson; }
  public void setThemeJson(String themeJson) { this.themeJson = themeJson; }
  public String getHomepageJson() { return homepageJson; }
  public void setHomepageJson(String homepageJson) { this.homepageJson = homepageJson; }
  public String getNavigationJson() { return navigationJson; }
  public void setNavigationJson(String navigationJson) { this.navigationJson = navigationJson; }
  public String getSeoJson() { return seoJson; }
  public void setSeoJson(String seoJson) { this.seoJson = seoJson; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public int getSortOrder() { return sortOrder; }
  public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
