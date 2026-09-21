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
@Table(name = "website_analytics_event")
public class WebsiteAnalyticsEvent {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(length = 255)
  private String host;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(length = 512)
  private String path;

  @Column(length = 1024)
  private String referrer;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "meta_json", nullable = false, columnDefinition = "jsonb")
  private String metaJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getHost() { return host; }
  public void setHost(String host) { this.host = host; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getPath() { return path; }
  public void setPath(String path) { this.path = path; }
  public String getReferrer() { return referrer; }
  public void setReferrer(String referrer) { this.referrer = referrer; }
  public String getUserAgent() { return userAgent; }
  public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
  public String getMetaJson() { return metaJson; }
  public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
