package com.sugamflow.school.exam.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_definition")
public class ExamDefinitionEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "term_key", nullable = false, length = 64)
  private String termKey;

  @Column(nullable = false, length = 191)
  private String name;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "section_id", nullable = false)
  private UUID sectionId;

  @Column(name = "max_marks", nullable = false, precision = 10, scale = 2)
  private BigDecimal maxMarks;

  @Column(nullable = false, length = 32)
  private String status = "DRAFT";

  @Column(name = "created_by", length = 128)
  private String createdBy;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "locked_at")
  private Instant lockedAt;

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
  public String getTermKey() { return termKey; }
  public void setTermKey(String termKey) { this.termKey = termKey; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public UUID getSubjectId() { return subjectId; }
  public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
  public UUID getSectionId() { return sectionId; }
  public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
  public BigDecimal getMaxMarks() { return maxMarks; }
  public void setMaxMarks(BigDecimal maxMarks) { this.maxMarks = maxMarks; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getPublishedAt() { return publishedAt; }
  public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
  public Instant getLockedAt() { return lockedAt; }
  public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
