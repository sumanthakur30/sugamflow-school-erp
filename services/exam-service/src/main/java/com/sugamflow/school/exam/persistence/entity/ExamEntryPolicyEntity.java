package com.sugamflow.school.exam.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "exam_entry_policy")
public class ExamEntryPolicyEntity {

  @Id
  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "absent_as_zero", nullable = false)
  private boolean absentAsZero = false;

  @Column(name = "allow_incomplete_submit", nullable = false)
  private boolean allowIncompleteSubmit = false;

  @Column(name = "allow_marks_when_excused", nullable = false)
  private boolean allowMarksWhenExcused = false;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public boolean isAbsentAsZero() { return absentAsZero; }
  public void setAbsentAsZero(boolean absentAsZero) { this.absentAsZero = absentAsZero; }
  public boolean isAllowIncompleteSubmit() { return allowIncompleteSubmit; }
  public void setAllowIncompleteSubmit(boolean allowIncompleteSubmit) {
    this.allowIncompleteSubmit = allowIncompleteSubmit;
  }
  public boolean isAllowMarksWhenExcused() { return allowMarksWhenExcused; }
  public void setAllowMarksWhenExcused(boolean allowMarksWhenExcused) {
    this.allowMarksWhenExcused = allowMarksWhenExcused;
  }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
