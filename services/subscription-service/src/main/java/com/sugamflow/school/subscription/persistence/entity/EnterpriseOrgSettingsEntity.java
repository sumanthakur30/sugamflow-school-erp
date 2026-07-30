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
@Table(name = "enterprise_org_settings")
public class EnterpriseOrgSettingsEntity {

  @Id
  @Column(name = "organization_id", length = 64)
  private String organizationId;

  @Column(name = "sso_enabled", nullable = false)
  private boolean ssoEnabled;

  @Column(name = "sso_provider", length = 32)
  private String ssoProvider;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "sso_config_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> ssoConfigJson = new LinkedHashMap<>();

  @Column(name = "white_label_enabled", nullable = false)
  private boolean whiteLabelEnabled;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "white_label_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> whiteLabelJson = new LinkedHashMap<>();

  @Column(name = "residency_region", length = 16)
  private String residencyRegion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ha_options_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> haOptionsJson = new LinkedHashMap<>();

  @Column(length = 512)
  private String notes;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public boolean isSsoEnabled() {
    return ssoEnabled;
  }

  public void setSsoEnabled(boolean ssoEnabled) {
    this.ssoEnabled = ssoEnabled;
  }

  public String getSsoProvider() {
    return ssoProvider;
  }

  public void setSsoProvider(String ssoProvider) {
    this.ssoProvider = ssoProvider;
  }

  public Map<String, Object> getSsoConfigJson() {
    return ssoConfigJson;
  }

  public void setSsoConfigJson(Map<String, Object> ssoConfigJson) {
    this.ssoConfigJson = ssoConfigJson;
  }

  public boolean isWhiteLabelEnabled() {
    return whiteLabelEnabled;
  }

  public void setWhiteLabelEnabled(boolean whiteLabelEnabled) {
    this.whiteLabelEnabled = whiteLabelEnabled;
  }

  public Map<String, Object> getWhiteLabelJson() {
    return whiteLabelJson;
  }

  public void setWhiteLabelJson(Map<String, Object> whiteLabelJson) {
    this.whiteLabelJson = whiteLabelJson;
  }

  public String getResidencyRegion() {
    return residencyRegion;
  }

  public void setResidencyRegion(String residencyRegion) {
    this.residencyRegion = residencyRegion;
  }

  public Map<String, Object> getHaOptionsJson() {
    return haOptionsJson;
  }

  public void setHaOptionsJson(Map<String, Object> haOptionsJson) {
    this.haOptionsJson = haOptionsJson;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
