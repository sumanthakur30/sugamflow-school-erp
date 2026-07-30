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
@Table(name = "usage_counter")
@IdClass(UsageCounterEntity.Pk.class)
public class UsageCounterEntity {

  @Id
  @Column(name = "organization_id", length = 64)
  private String organizationId;

  @Id
  @Column(name = "limit_code", length = 64)
  private String limitCode;

  @Id
  @Column(name = "period_key", length = 32)
  private String periodKey = "ALL";

  @Column(name = "used_value", nullable = false)
  private long usedValue;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getLimitCode() {
    return limitCode;
  }

  public void setLimitCode(String limitCode) {
    this.limitCode = limitCode;
  }

  public String getPeriodKey() {
    return periodKey;
  }

  public void setPeriodKey(String periodKey) {
    this.periodKey = periodKey;
  }

  public long getUsedValue() {
    return usedValue;
  }

  public void setUsedValue(long usedValue) {
    this.usedValue = usedValue;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public static class Pk implements Serializable {
    private String organizationId;
    private String limitCode;
    private String periodKey;

    public Pk() {}

    public Pk(String organizationId, String limitCode, String periodKey) {
      this.organizationId = organizationId;
      this.limitCode = limitCode;
      this.periodKey = periodKey;
    }

    public String getOrganizationId() {
      return organizationId;
    }

    public void setOrganizationId(String organizationId) {
      this.organizationId = organizationId;
    }

    public String getLimitCode() {
      return limitCode;
    }

    public void setLimitCode(String limitCode) {
      this.limitCode = limitCode;
    }

    public String getPeriodKey() {
      return periodKey;
    }

    public void setPeriodKey(String periodKey) {
      this.periodKey = periodKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(organizationId, pk.organizationId)
          && Objects.equals(limitCode, pk.limitCode)
          && Objects.equals(periodKey, pk.periodKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(organizationId, limitCode, periodKey);
    }
  }
}
