package com.sugamflow.school.academic.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One-day cover for a single timetable period. The weekly slot stays unchanged. */
@Entity
@Table(name = "timetable_substitute")
public class TimetableSubstituteEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "slot_id", nullable = false)
  private UUID slotId;

  @Column(name = "substitute_date", nullable = false)
  private LocalDate substituteDate;

  @Column(name = "teacher_username", nullable = false, length = 128)
  private String teacherUsername;

  @Column(length = 256)
  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public UUID getSlotId() { return slotId; }
  public void setSlotId(UUID slotId) { this.slotId = slotId; }
  public LocalDate getSubstituteDate() { return substituteDate; }
  public void setSubstituteDate(LocalDate substituteDate) { this.substituteDate = substituteDate; }
  public String getTeacherUsername() { return teacherUsername; }
  public void setTeacherUsername(String teacherUsername) { this.teacherUsername = teacherUsername; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
