package com.sugamflow.school.subscription.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "plan_feature")
@IdClass(PlanFeatureEntity.Pk.class)
public class PlanFeatureEntity {

  @Id
  @Column(name = "plan_id", length = 64)
  private String planId;

  @Id
  @Column(name = "feature_code", length = 96)
  private String featureCode;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getPlanId() {
    return planId;
  }

  public void setPlanId(String planId) {
    this.planId = planId;
  }

  public String getFeatureCode() {
    return featureCode;
  }

  public void setFeatureCode(String featureCode) {
    this.featureCode = featureCode;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public static class Pk implements Serializable {
    private String planId;
    private String featureCode;

    public Pk() {}

    public Pk(String planId, String featureCode) {
      this.planId = planId;
      this.featureCode = featureCode;
    }

    public String getPlanId() {
      return planId;
    }

    public void setPlanId(String planId) {
      this.planId = planId;
    }

    public String getFeatureCode() {
      return featureCode;
    }

    public void setFeatureCode(String featureCode) {
      this.featureCode = featureCode;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(planId, pk.planId) && Objects.equals(featureCode, pk.featureCode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(planId, featureCode);
    }
  }
}
