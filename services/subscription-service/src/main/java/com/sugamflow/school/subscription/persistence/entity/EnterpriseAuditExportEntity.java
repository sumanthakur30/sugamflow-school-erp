package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "enterprise_audit_export")
public class EnterpriseAuditExportEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(nullable = false, length = 16)
  private String status = "READY";

  @Column(name = "from_at")
  private Instant fromAt;

  @Column(name = "to_at")
  private Instant toAt;

  @Column(nullable = false, length = 16)
  private String format = "CSV";

  @Column(name = "row_count", nullable = false)
  private int rowCount;

  @Column(name = "content_text", columnDefinition = "TEXT")
  private String contentText;

  @Column(name = "requested_by", length = 128)
  private String requestedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getFromAt() {
    return fromAt;
  }

  public void setFromAt(Instant fromAt) {
    this.fromAt = fromAt;
  }

  public Instant getToAt() {
    return toAt;
  }

  public void setToAt(Instant toAt) {
    this.toAt = toAt;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public int getRowCount() {
    return rowCount;
  }

  public void setRowCount(int rowCount) {
    this.rowCount = rowCount;
  }

  public String getContentText() {
    return contentText;
  }

  public void setContentText(String contentText) {
    this.contentText = contentText;
  }

  public String getRequestedBy() {
    return requestedBy;
  }

  public void setRequestedBy(String requestedBy) {
    this.requestedBy = requestedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
