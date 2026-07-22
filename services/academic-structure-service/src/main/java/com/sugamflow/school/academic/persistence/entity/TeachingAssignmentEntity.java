package com.sugamflow.school.academic.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Links a teacher (auth username) to a subject taught in a section. Drives teacher RBAC. */
@Entity
@Table(name = "teaching_assignment")
public class TeachingAssignmentEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "section_id", nullable = false)
  private UUID sectionId;

  @Column(name = "subject_id")
  private UUID subjectId;

  @Column(name = "teacher_username", nullable = false, length = 128)
  private String teacherUsername;

  @Column(name = "is_class_teacher", nullable = false)
  private boolean classTeacher;

  @Column(name = "weekly_periods", nullable = false)
  private int weeklyPeriods = 4;

  @Column(name = "max_daily_periods", nullable = false)
  private int maxDailyPeriods = 2;

  @Column(name = "preferred_room", length = 64)
  private String preferredRoom;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "unavailable_slots", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> unavailableSlots = new ArrayList<>();

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

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
  public UUID getSectionId() { return sectionId; }
  public void setSectionId(UUID sectionId) { this.sectionId = sectionId; }
  public UUID getSubjectId() { return subjectId; }
  public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
  public String getTeacherUsername() { return teacherUsername; }
  public void setTeacherUsername(String teacherUsername) { this.teacherUsername = teacherUsername; }
  public boolean isClassTeacher() { return classTeacher; }
  public void setClassTeacher(boolean classTeacher) { this.classTeacher = classTeacher; }
  public int getWeeklyPeriods() { return weeklyPeriods; }
  public void setWeeklyPeriods(int weeklyPeriods) { this.weeklyPeriods = weeklyPeriods; }
  public int getMaxDailyPeriods() { return maxDailyPeriods; }
  public void setMaxDailyPeriods(int maxDailyPeriods) { this.maxDailyPeriods = maxDailyPeriods; }
  public String getPreferredRoom() { return preferredRoom; }
  public void setPreferredRoom(String preferredRoom) { this.preferredRoom = preferredRoom; }
  public List<Map<String, Object>> getUnavailableSlots() { return unavailableSlots; }
  public void setUnavailableSlots(List<Map<String, Object>> unavailableSlots) {
    this.unavailableSlots = unavailableSlots == null ? new ArrayList<>() : unavailableSlots;
  }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
