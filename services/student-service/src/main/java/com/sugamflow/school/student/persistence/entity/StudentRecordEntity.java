package com.sugamflow.school.student.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "student_record")
public class StudentRecordEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "form_key", nullable = false, length = 128)
  private String formKey;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "source_application_id")
  private UUID sourceApplicationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> answers = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> history = new ArrayList<>();

  @Column(name = "created_by", length = 128)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by", length = 128)
  private String deletedBy;

  @Column(name = "delete_reason", columnDefinition = "text")
  private String deleteReason;

  @Column(name = "restored_at")
  private Instant restoredAt;

  @Column(name = "restored_by", length = 128)
  private String restoredBy;

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

  public String getAcademicSessionId() {
    return academicSessionId;
  }

  public void setAcademicSessionId(String academicSessionId) {
    this.academicSessionId = academicSessionId;
  }

  public String getFormKey() {
    return formKey;
  }

  public void setFormKey(String formKey) {
    this.formKey = formKey;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getAdmissionNo() {
    return admissionNo;
  }

  public void setAdmissionNo(String admissionNo) {
    this.admissionNo = admissionNo;
  }

  public UUID getSourceApplicationId() {
    return sourceApplicationId;
  }

  public void setSourceApplicationId(UUID sourceApplicationId) {
    this.sourceApplicationId = sourceApplicationId;
  }

  public Map<String, Object> getAnswers() {
    return answers;
  }

  public void setAnswers(Map<String, Object> answers) {
    this.answers = answers;
  }

  public List<Map<String, Object>> getHistory() {
    return history;
  }

  public void setHistory(List<Map<String, Object>> history) {
    this.history = history;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
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

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(String deletedBy) {
    this.deletedBy = deletedBy;
  }

  public String getDeleteReason() {
    return deleteReason;
  }

  public void setDeleteReason(String deleteReason) {
    this.deleteReason = deleteReason;
  }

  public Instant getRestoredAt() {
    return restoredAt;
  }

  public void setRestoredAt(Instant restoredAt) {
    this.restoredAt = restoredAt;
  }

  public String getRestoredBy() {
    return restoredBy;
  }

  public void setRestoredBy(String restoredBy) {
    this.restoredBy = restoredBy;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
