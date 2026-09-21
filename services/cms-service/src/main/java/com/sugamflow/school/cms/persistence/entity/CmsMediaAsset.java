package com.sugamflow.school.cms.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cms_media_asset")
public class CmsMediaAsset {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "file_name", nullable = false, length = 256)
  private String fileName;

  @Column(name = "content_type", length = 128)
  private String contentType;

  @Column(nullable = false, length = 1024)
  private String url;

  @Column(name = "byte_size")
  private Long byteSize;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getFileName() { return fileName; }
  public void setFileName(String fileName) { this.fileName = fileName; }
  public String getContentType() { return contentType; }
  public void setContentType(String contentType) { this.contentType = contentType; }
  public String getUrl() { return url; }
  public void setUrl(String url) { this.url = url; }
  public Long getByteSize() { return byteSize; }
  public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
