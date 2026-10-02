package com.sugamflow.school.student.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_desk_note")
public class StudentDeskNoteEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "student_id", nullable = false)
  private UUID studentId;

  @Column(nullable = false, length = 32)
  private String kind;

  @Column(length = 32)
  private String channel;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "created_by", length = 128)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public UUID getStudentId() { return studentId; }
  public void setStudentId(UUID studentId) { this.studentId = studentId; }
  public String getKind() { return kind; }
  public void setKind(String kind) { this.kind = kind; }
  public String getChannel() { return channel; }
  public void setChannel(String channel) { this.channel = channel; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
