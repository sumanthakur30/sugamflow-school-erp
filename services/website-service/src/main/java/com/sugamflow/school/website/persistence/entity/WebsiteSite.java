package com.sugamflow.school.website.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "website_site")
public class WebsiteSite {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  /** ERP campus key (org_branch.branch_key); default {@code main}. */
  @Column(name = "branch_id", nullable = false, length = 64)
  private String branchId = "main";

  @Column(name = "is_default", nullable = false)
  private boolean defaultSite = true;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "template_code", length = 64)
  private String templateCode;

  @Column(name = "display_name", nullable = false, length = 256)
  private String displayName;

  @Column(name = "erp_login_url", length = 512)
  private String erpLoginUrl;

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

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getBranchId() {
    return branchId;
  }

  public void setBranchId(String branchId) {
    this.branchId = branchId;
  }

  public boolean isDefaultSite() {
    return defaultSite;
  }

  public void setDefaultSite(boolean defaultSite) {
    this.defaultSite = defaultSite;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getTemplateCode() {
    return templateCode;
  }

  public void setTemplateCode(String templateCode) {
    this.templateCode = templateCode;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getErpLoginUrl() {
    return erpLoginUrl;
  }

  public void setErpLoginUrl(String erpLoginUrl) {
    this.erpLoginUrl = erpLoginUrl;
  }

  public String getThemeJson() {
    return themeJson;
  }

  public void setThemeJson(String themeJson) {
    this.themeJson = themeJson;
  }

  public String getHomepageJson() {
    return homepageJson;
  }

  public void setHomepageJson(String homepageJson) {
    this.homepageJson = homepageJson;
  }

  public String getNavigationJson() {
    return navigationJson;
  }

  public void setNavigationJson(String navigationJson) {
    this.navigationJson = navigationJson;
  }

  public String getSeoJson() {
    return seoJson;
  }

  public void setSeoJson(String seoJson) {
    this.seoJson = seoJson;
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
