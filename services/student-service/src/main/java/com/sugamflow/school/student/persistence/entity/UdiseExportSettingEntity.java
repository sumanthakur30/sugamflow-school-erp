package com.sugamflow.school.student.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "udise_export_setting")
public class UdiseExportSettingEntity {

  @Id
  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "column_keys", nullable = false, columnDefinition = "jsonb")
  private List<String> columnKeys = new ArrayList<>();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public List<String> getColumnKeys() {
    return columnKeys;
  }

  public void setColumnKeys(List<String> columnKeys) {
    this.columnKeys = columnKeys != null ? columnKeys : new ArrayList<>();
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
