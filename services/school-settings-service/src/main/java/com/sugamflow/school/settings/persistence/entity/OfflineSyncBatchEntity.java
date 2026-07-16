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
@Table(name = "offline_sync_batch")
public class OfflineSyncBatchEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "client_id", length = 128)
  private String clientId;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "item_count", nullable = false)
  private int itemCount;

  @Column(name = "success_count", nullable = false)
  private int successCount;

  @Column(name = "failure_count", nullable = false)
  private int failureCount;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payloadJson = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> resultJson = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getItemCount() { return itemCount; }
  public void setItemCount(int itemCount) { this.itemCount = itemCount; }
  public int getSuccessCount() { return successCount; }
  public void setSuccessCount(int successCount) { this.successCount = successCount; }
  public int getFailureCount() { return failureCount; }
  public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
  public Map<String, Object> getPayloadJson() { return payloadJson; }
  public void setPayloadJson(Map<String, Object> payloadJson) { this.payloadJson = payloadJson; }
  public Map<String, Object> getResultJson() { return resultJson; }
  public void setResultJson(Map<String, Object> resultJson) { this.resultJson = resultJson; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
