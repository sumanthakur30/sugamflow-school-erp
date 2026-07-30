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
@Table(name = "credit_wallet")
@IdClass(CreditWalletEntity.Pk.class)
public class CreditWalletEntity {

  @Id
  @Column(name = "organization_id", length = 64)
  private String organizationId;

  @Id
  @Column(name = "meter_code", length = 64)
  private String meterCode;

  @Column(nullable = false)
  private long balance;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getMeterCode() {
    return meterCode;
  }

  public void setMeterCode(String meterCode) {
    this.meterCode = meterCode;
  }

  public long getBalance() {
    return balance;
  }

  public void setBalance(long balance) {
    this.balance = balance;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public static class Pk implements Serializable {
    private String organizationId;
    private String meterCode;

    public Pk() {}

    public Pk(String organizationId, String meterCode) {
      this.organizationId = organizationId;
      this.meterCode = meterCode;
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
          && Objects.equals(meterCode, pk.meterCode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(organizationId, meterCode);
    }
  }
}
