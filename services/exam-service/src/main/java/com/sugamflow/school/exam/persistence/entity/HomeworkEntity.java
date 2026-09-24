package com.sugamflow.school.exam.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lms_homework")
public class HomeworkEntity {

  public static final String STATUS_DRAFT = "DRAFT";
  public static final String STATUS_PUBLISHED = "PUBLISHED";
  public static final String STATUS_CLOSED = "CLOSED";

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "subject_key", length = 64)
  private String subjectKey;

  @Column(name = "class_section", length = 128)
  private String classSection;

  @Column(name = "section_id")
  private UUID sectionId;

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(nullable = false, length = 32)
  private String status = STATUS_PUBLISHED;

  @Column(name = "created_by", length = 128)
  private String createdBy;

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
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getSubjectKey() { return subjectKey; }
  public void setSubjectKey(String subjectKey) { this.subjectKey = subjectKey; }
  public String getClassSection() { return classSection; }
  public void setClassSection(String classSection) { this.classSection = classSection; }
  public UUID getSectionId() { return sectionId; }
  public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
  public Instant getDueAt() { return dueAt; }
  public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
