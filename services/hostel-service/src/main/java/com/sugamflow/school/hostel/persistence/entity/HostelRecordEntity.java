package com.sugamflow.school.hostel.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "hostel_record")
public class HostelRecordEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "form_key", nullable = false, length = 128)
  private String formKey;

  @Column(name = "workflow_key", nullable = false, length = 128)
  private String workflowKey;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "current_step_sequence", nullable = false)
  private int currentStepSequence = 1;

  @Column(name = "current_step_name", length = 128)
  private String currentStepName;

  @Column(name = "assignee_role", length = 64)
  private String assigneeRole;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> answers = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> history = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "matched_actions", nullable = false, columnDefinition = "jsonb")
  private List<String> matchedActions = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "notification_intents", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> notificationIntents = new ArrayList<>();

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
  public String getFormKey() { return formKey; }
  public void setFormKey(String formKey) { this.formKey = formKey; }
  public String getWorkflowKey() { return workflowKey; }
  public void setWorkflowKey(String workflowKey) { this.workflowKey = workflowKey; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getCurrentStepSequence() { return currentStepSequence; }
  public void setCurrentStepSequence(int currentStepSequence) { this.currentStepSequence = currentStepSequence; }
  public String getCurrentStepName() { return currentStepName; }
  public void setCurrentStepName(String currentStepName) { this.currentStepName = currentStepName; }
  public String getAssigneeRole() { return assigneeRole; }
  public void setAssigneeRole(String assigneeRole) { this.assigneeRole = assigneeRole; }
  public Map<String, Object> getAnswers() { return answers; }
  public void setAnswers(Map<String, Object> answers) { this.answers = answers; }
  public List<Map<String, Object>> getHistory() { return history; }
  public void setHistory(List<Map<String, Object>> history) { this.history = history; }
  public List<String> getMatchedActions() { return matchedActions; }
  public void setMatchedActions(List<String> matchedActions) { this.matchedActions = matchedActions; }
  public List<Map<String, Object>> getNotificationIntents() { return notificationIntents; }
  public void setNotificationIntents(List<Map<String, Object>> notificationIntents) {
    this.notificationIntents = notificationIntents;
  }

  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
