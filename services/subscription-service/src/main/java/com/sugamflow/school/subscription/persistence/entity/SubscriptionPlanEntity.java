package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "subscription_plan")
public class SubscriptionPlanEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, unique = true, length = 64)
  private String code;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "plan_type", nullable = false, length = 64)
  private String planType;

  @Column(nullable = false)
  private boolean active = true;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "limits_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> limitsJson = new LinkedHashMap<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "feature_flags_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> featureFlagsJson = new LinkedHashMap<>();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getPlanType() { return planType; }
  public void setPlanType(String planType) { this.planType = planType; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Map<String, Object> getLimitsJson() { return limitsJson; }
  public void setLimitsJson(Map<String, Object> limitsJson) { this.limitsJson = limitsJson; }
  public Map<String, Object> getFeatureFlagsJson() { return featureFlagsJson; }
  public void setFeatureFlagsJson(Map<String, Object> featureFlagsJson) {
    this.featureFlagsJson = featureFlagsJson;
  }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
