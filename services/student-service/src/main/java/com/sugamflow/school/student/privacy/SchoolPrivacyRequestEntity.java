package com.sugamflow.school.student.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_privacy_request")
public class SchoolPrivacyRequestEntity {

  @Id private UUID id;

  @Column(name = "request_number", nullable = false, length = 40)
  private String requestNumber;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "subject_ref", nullable = false, length = 128)
  private String subjectRef;

  @Column(name = "data_category", nullable = false, length = 20)
  private String dataCategory;

  @Column(name = "subject_label", length = 80)
  private String subjectLabel;

  @Column(name = "request_type", nullable = false, length = 40)
  private String requestType;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(columnDefinition = "TEXT")
  private String detail;

  @Column(name = "action_taken", nullable = false, length = 40)
  private String actionTaken;

  @Column(name = "response_note", columnDefinition = "TEXT")
  private String responseNote;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getRequestNumber() { return requestNumber; }
  public void setRequestNumber(String requestNumber) { this.requestNumber = requestNumber; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getSubjectRef() { return subjectRef; }
  public void setSubjectRef(String subjectRef) { this.subjectRef = subjectRef; }
  public String getDataCategory() { return dataCategory; }
  public void setDataCategory(String dataCategory) { this.dataCategory = dataCategory; }
  public String getSubjectLabel() { return subjectLabel; }
  public void setSubjectLabel(String subjectLabel) { this.subjectLabel = subjectLabel; }
  public String getRequestType() { return requestType; }
  public void setRequestType(String requestType) { this.requestType = requestType; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getDetail() { return detail; }
  public void setDetail(String detail) { this.detail = detail; }
  public String getActionTaken() { return actionTaken; }
  public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
  public String getResponseNote() { return responseNote; }
  public void setResponseNote(String responseNote) { this.responseNote = responseNote; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
