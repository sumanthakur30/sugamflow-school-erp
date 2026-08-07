package com.sugamflow.school.compliance.persistence.entity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "disclosure_binding")
public class DisclosureBindingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 100)
  private String organizationId;

  @Column(name = "section_key", nullable = false, length = 80)
  private String sectionKey = "MAIN";

  @Column(name = "cms_slug", nullable = false, length = 160)
  private String cmsSlug = "mandatory-public-disclosure";

  @Column(name = "cms_page_id", length = 64)
  private String cmsPageId;

  @Column(nullable = false)
  private String title = "Mandatory Public Disclosure";

  @Column(name = "last_published_at")
  private Instant lastPublishedAt;

  @Column(name = "last_publish_status", length = 40)
  private String lastPublishStatus;

  @Column(name = "last_publish_message", columnDefinition = "TEXT")
  private String lastPublishMessage;

  @Column(name = "public_url_hint", length = 500)
  private String publicUrlHint;

  @Column(name = "published_html", columnDefinition = "TEXT")
  private String publishedHtml;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "published_snapshot", columnDefinition = "jsonb")
  private Map<String, Object> publishedSnapshot = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getSectionKey() {
    return sectionKey;
  }

  public void setSectionKey(String sectionKey) {
    this.sectionKey = sectionKey;
  }

  public String getCmsSlug() {
    return cmsSlug;
  }

  public void setCmsSlug(String cmsSlug) {
    this.cmsSlug = cmsSlug;
  }

  public String getCmsPageId() {
    return cmsPageId;
  }

  public void setCmsPageId(String cmsPageId) {
    this.cmsPageId = cmsPageId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Instant getLastPublishedAt() {
    return lastPublishedAt;
  }

  public void setLastPublishedAt(Instant lastPublishedAt) {
    this.lastPublishedAt = lastPublishedAt;
  }

  public String getLastPublishStatus() {
    return lastPublishStatus;
  }

  public void setLastPublishStatus(String lastPublishStatus) {
    this.lastPublishStatus = lastPublishStatus;
  }

  public String getLastPublishMessage() {
    return lastPublishMessage;
  }

  public void setLastPublishMessage(String lastPublishMessage) {
    this.lastPublishMessage = lastPublishMessage;
  }

  public String getPublicUrlHint() {
    return publicUrlHint;
  }

  public void setPublicUrlHint(String publicUrlHint) {
    this.publicUrlHint = publicUrlHint;
  }

  public String getPublishedHtml() {
    return publishedHtml;
  }

  public void setPublishedHtml(String publishedHtml) {
    this.publishedHtml = publishedHtml;
  }

  public Map<String, Object> getPublishedSnapshot() {
    return publishedSnapshot != null ? publishedSnapshot : Map.of();
  }

  public void setPublishedSnapshot(Map<String, Object> publishedSnapshot) {
    this.publishedSnapshot = publishedSnapshot;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
