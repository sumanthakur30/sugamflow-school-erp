package com.sugamflow.school.compliance.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "compliance_field_map")
public class ComplianceFieldMapEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "board_code", nullable = false, length = 40)
  private String boardCode;

  @Column(name = "entity_type", nullable = false, length = 40)
  private String entityType;

  @Column(name = "field_key", nullable = false, length = 100)
  private String fieldKey;

  @Column(name = "source_path", nullable = false, length = 200)
  private String sourcePath;

  @Column(nullable = false, length = 160)
  private String label;

  @Column(nullable = false)
  private boolean required;

  @Column(nullable = false, length = 20)
  private String severity = "BLOCKER";

  @Column(name = "format_regex", length = 255)
  private String formatRegex;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active = true;

  public Long getId() {
    return id;
  }

  public String getBoardCode() {
    return boardCode;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getFieldKey() {
    return fieldKey;
  }

  public String getSourcePath() {
    return sourcePath;
  }

  public String getLabel() {
    return label;
  }

  public boolean isRequired() {
    return required;
  }

  public String getSeverity() {
    return severity;
  }

  public String getFormatRegex() {
    return formatRegex;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public void setFormatRegex(String formatRegex) {
    this.formatRegex = formatRegex;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
