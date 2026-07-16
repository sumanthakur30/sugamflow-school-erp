package com.sugamflow.school.fee.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "finance_transaction")
public class FinanceTransactionEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "transaction_type", nullable = false, length = 64)
  private String transactionType;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "student_ref", length = 128)
  private String studentRef;

  @Column(nullable = false, length = 8)
  private String currency = "INR";

  @Column(name = "gross_amount", precision = 14, scale = 2)
  private BigDecimal grossAmount;

  @Column(name = "net_amount", precision = 14, scale = 2)
  private BigDecimal netAmount;

  @Column(name = "reference_no", length = 128)
  private String referenceNo;

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey;

  @Column(name = "source_collection_id")
  private UUID sourceCollectionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload = new LinkedHashMap<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getAcademicSessionId() { return academicSessionId; }
  public void setAcademicSessionId(String academicSessionId) { this.academicSessionId = academicSessionId; }
  public String getTransactionType() { return transactionType; }
  public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getStudentRef() { return studentRef; }
  public void setStudentRef(String studentRef) { this.studentRef = studentRef; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public BigDecimal getGrossAmount() { return grossAmount; }
  public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
  public BigDecimal getNetAmount() { return netAmount; }
  public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
  public String getReferenceNo() { return referenceNo; }
  public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
  public UUID getSourceCollectionId() { return sourceCollectionId; }
  public void setSourceCollectionId(UUID sourceCollectionId) { this.sourceCollectionId = sourceCollectionId; }
  public Map<String, Object> getPayload() { return payload; }
  public void setPayload(Map<String, Object> payload) { this.payload = payload; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
