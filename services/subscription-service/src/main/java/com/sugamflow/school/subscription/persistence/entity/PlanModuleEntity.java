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
@Table(name = "plan_module")
@IdClass(PlanModuleEntity.Pk.class)
public class PlanModuleEntity {

  @Id
  @Column(name = "plan_id", length = 64)
  private String planId;

  @Id
  @Column(name = "module_code", length = 64)
  private String moduleCode;

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

  public String getModuleCode() {
    return moduleCode;
  }

  public void setModuleCode(String moduleCode) {
    this.moduleCode = moduleCode;
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
    private String moduleCode;

    public Pk() {}

    public Pk(String planId, String moduleCode) {
      this.planId = planId;
      this.moduleCode = moduleCode;
    }

    public String getPlanId() {
      return planId;
    }

    public void setPlanId(String planId) {
      this.planId = planId;
    }

    public String getModuleCode() {
      return moduleCode;
    }

    public void setModuleCode(String moduleCode) {
      this.moduleCode = moduleCode;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(planId, pk.planId) && Objects.equals(moduleCode, pk.moduleCode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(planId, moduleCode);
    }
  }
}
