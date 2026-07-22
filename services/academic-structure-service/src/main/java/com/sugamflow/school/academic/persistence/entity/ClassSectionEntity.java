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

/**
 * A section within a class (e.g. "8-A"). {@code studentLabel} is the string that appears on student
 * records ({@code classApplied} / {@code classSection}) so teacher scoping can match reliably.
 */
@Entity
@Table(name = "class_section")
public class ClassSectionEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "class_id", nullable = false)
  private UUID classId;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(length = 64)
  private String code;

  @Column(name = "student_label", length = 191)
  private String studentLabel;

  @Column(name = "class_teacher_username", length = 128)
  private String classTeacherUsername;

  @Column(length = 64)
  private String room;

  @Column(name = "capacity")
  private Integer capacity;

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
  public UUID getClassId() { return classId; }
  public void setClassId(UUID classId) { this.classId = classId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getStudentLabel() { return studentLabel; }
  public void setStudentLabel(String studentLabel) { this.studentLabel = studentLabel; }
  public String getClassTeacherUsername() { return classTeacherUsername; }
  public void setClassTeacherUsername(String classTeacherUsername) { this.classTeacherUsername = classTeacherUsername; }
  public String getRoom() { return room; }
  public void setRoom(String room) { this.room = room; }
  public Integer getCapacity() { return capacity; }
  public void setCapacity(Integer capacity) { this.capacity = capacity; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Map<String, Object> getAttributes() { return attributes; }
  public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
