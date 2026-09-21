package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "plan_version")
public class PlanVersionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "plan_id", nullable = false, length = 64)
  private String planId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(nullable = false, length = 16)
  private String status = "DRAFT";

  @Column(length = 128)
  private String label;

  @Column(length = 512)
  private String notes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "feature_flags_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> featureFlagsJson = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "limits_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> limitsJson = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "module_order_json", nullable = false, columnDefinition = "jsonb")
  private List<String> moduleOrderJson = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "published_at")
  private Instant publishedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getPlanId() {
    return planId;
  }

  public void setPlanId(String planId) {
    this.planId = planId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public void setVersionNumber(int versionNumber) {
    this.versionNumber = versionNumber;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Map<String, Object> getFeatureFlagsJson() {
    return featureFlagsJson;
  }

  public void setFeatureFlagsJson(Map<String, Object> featureFlagsJson) {
    this.featureFlagsJson = featureFlagsJson;
  }

  public Map<String, Object> getLimitsJson() {
    return limitsJson;
  }

  public void setLimitsJson(Map<String, Object> limitsJson) {
    this.limitsJson = limitsJson;
  }

  public List<String> getModuleOrderJson() {
    return moduleOrderJson;
  }

  public void setModuleOrderJson(List<String> moduleOrderJson) {
    this.moduleOrderJson = moduleOrderJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Instant publishedAt) {
    this.publishedAt = publishedAt;
  }
}
