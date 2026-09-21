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
@Table(name = "plan_limit")
@IdClass(PlanLimitEntity.Pk.class)
public class PlanLimitEntity {

  @Id
  @Column(name = "plan_id", length = 64)
  private String planId;

  @Id
  @Column(name = "limit_code", length = 64)
  private String limitCode;

  @Column(name = "limit_value", nullable = false)
  private long limitValue;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getPlanId() {
    return planId;
  }

  public void setPlanId(String planId) {
    this.planId = planId;
  }

  public String getLimitCode() {
    return limitCode;
  }

  public void setLimitCode(String limitCode) {
    this.limitCode = limitCode;
  }

  public long getLimitValue() {
    return limitValue;
  }

  public void setLimitValue(long limitValue) {
    this.limitValue = limitValue;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public static class Pk implements Serializable {
    private String planId;
    private String limitCode;

    public Pk() {}

    public Pk(String planId, String limitCode) {
      this.planId = planId;
      this.limitCode = limitCode;
    }

    public String getPlanId() {
      return planId;
    }

    public void setPlanId(String planId) {
      this.planId = planId;
    }

    public String getLimitCode() {
      return limitCode;
    }

    public void setLimitCode(String limitCode) {
      this.limitCode = limitCode;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(planId, pk.planId) && Objects.equals(limitCode, pk.limitCode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(planId, limitCode);
    }
  }
}
