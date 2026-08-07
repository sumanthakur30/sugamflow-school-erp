package com.sugamflow.school.compliance.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "approval_step")
public class ApprovalStepEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, length = 100)
  private String organizationId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Column(name = "step_code", nullable = false, length = 40)
  private String stepCode;

  @Column(nullable = false, length = 40)
  private String decision = "PENDING";

  @Column(name = "actor_user_id", length = 120)
  private String actorUserId;

  @Column(name = "comment_text", columnDefinition = "TEXT")
  private String commentText;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public Long getId() {
    return id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public Long getCampaignId() {
    return campaignId;
  }

  public void setCampaignId(Long campaignId) {
    this.campaignId = campaignId;
  }

  public String getStepCode() {
    return stepCode;
  }

  public void setStepCode(String stepCode) {
    this.stepCode = stepCode;
  }

  public String getDecision() {
    return decision;
  }

  public void setDecision(String decision) {
    this.decision = decision;
  }

  public String getActorUserId() {
    return actorUserId;
  }

  public void setActorUserId(String actorUserId) {
    this.actorUserId = actorUserId;
  }

  public String getCommentText() {
    return commentText;
  }

  public void setCommentText(String commentText) {
    this.commentText = commentText;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(Instant decidedAt) {
    this.decidedAt = decidedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
