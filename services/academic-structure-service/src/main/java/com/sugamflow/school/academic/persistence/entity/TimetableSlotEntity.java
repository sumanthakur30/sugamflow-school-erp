package com.sugamflow.school.academic.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A scheduled slot: which subject/teacher a section has on a given weekday + period. */
@Entity
@Table(name = "timetable_slot")
public class TimetableSlotEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "section_id", nullable = false)
  private UUID sectionId;

  @Column(name = "day_of_week", nullable = false)
  private int dayOfWeek;

  @Column(name = "period_id", nullable = false)
  private UUID periodId;

  @Column(name = "subject_id")
  private UUID subjectId;

  @Column(name = "teacher_username", length = 128)
  private String teacherUsername;

  @Column(length = 64)
  private String room;

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
  public int getDayOfWeek() { return dayOfWeek; }
  public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
  public UUID getPeriodId() { return periodId; }
  public void setPeriodId(UUID periodId) { this.periodId = periodId; }
  public UUID getSubjectId() { return subjectId; }
  public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }
  public String getTeacherUsername() { return teacherUsername; }
  public void setTeacherUsername(String teacherUsername) { this.teacherUsername = teacherUsername; }
  public String getRoom() { return room; }
  public void setRoom(String room) { this.room = room; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
