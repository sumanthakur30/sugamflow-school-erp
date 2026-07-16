package com.sugamflow.school.settings.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "offline_sync_item")
public class OfflineSyncItemEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "batch_id", nullable = false, length = 64)
  private String batchId;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "client_item_id", length = 128)
  private String clientItemId;

  @Column(name = "entity_type", nullable = false, length = 64)
  private String entityType;

  @Column(nullable = false, length = 32)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "request_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> requestJson = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_json", columnDefinition = "jsonb")
  private Map<String, Object> responseJson;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getBatchId() { return batchId; }
  public void setBatchId(String batchId) { this.batchId = batchId; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getClientItemId() { return clientItemId; }
  public void setClientItemId(String clientItemId) { this.clientItemId = clientItemId; }
  public String getEntityType() { return entityType; }
  public void setEntityType(String entityType) { this.entityType = entityType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Map<String, Object> getRequestJson() { return requestJson; }
  public void setRequestJson(Map<String, Object> requestJson) { this.requestJson = requestJson; }
  public Map<String, Object> getResponseJson() { return responseJson; }
  public void setResponseJson(Map<String, Object> responseJson) { this.responseJson = responseJson; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
