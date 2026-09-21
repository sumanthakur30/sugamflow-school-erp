package com.sugamflow.school.student.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_job_row")
public class ImportJobRowEntity {

  @Id private UUID id;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "row_number", nullable = false)
  private int rowNumber;

  @Column(nullable = false, length = 32)
  private String status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> rawJson = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "mapped_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> mappedJson = new LinkedHashMap<>();

  @Column(name = "error_message", length = 1024)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getJobId() { return jobId; }
  public void setJobId(UUID jobId) { this.jobId = jobId; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public int getRowNumber() { return rowNumber; }
  public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Map<String, Object> getRawJson() { return rawJson; }
  public void setRawJson(Map<String, Object> rawJson) { this.rawJson = rawJson; }
  public Map<String, Object> getMappedJson() { return mappedJson; }
  public void setMappedJson(Map<String, Object> mappedJson) { this.mappedJson = mappedJson; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
