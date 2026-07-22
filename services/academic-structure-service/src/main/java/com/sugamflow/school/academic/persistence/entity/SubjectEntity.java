package com.sugamflow.school.academic.persistence.entity;

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

/** A subject offered by the school (e.g. "Mathematics"). */
@Entity
@Table(name = "subject")
public class SubjectEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(length = 64)
  private String code;

  @Column(name = "subject_type", length = 32)
  private String subjectType = "CORE";

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attributes = new LinkedHashMap<>();

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
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getSubjectType() { return subjectType; }
  public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Map<String, Object> getAttributes() { return attributes; }
  public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
