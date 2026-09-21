package com.sugamflow.school.compliance.persistence.entity;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "validation_rule")
public class ValidationRuleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "board_code", nullable = false, length = 40)
  private String boardCode;

  @Column(name = "rule_code", nullable = false, length = 80)
  private String ruleCode;

  @Column(name = "entity_type", nullable = false, length = 40)
  private String entityType;

  @Column(name = "rule_type", nullable = false, length = 40)
  private String ruleType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> configJson = new LinkedHashMap<>();

  @Column(nullable = false, length = 20)
  private String severity = "BLOCKER";

  @Column(name = "message_template", length = 500)
  private String messageTemplate;

  @Column(nullable = false)
  private boolean active = true;

  public Long getId() {
    return id;
  }

  public String getBoardCode() {
    return boardCode;
  }

  public String getRuleCode() {
    return ruleCode;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getRuleType() {
    return ruleType;
  }

  public Map<String, Object> getConfigJson() {
    return configJson != null ? configJson : Map.of();
  }

  public String getSeverity() {
    return severity;
  }

  public String getMessageTemplate() {
    return messageTemplate;
  }

  public boolean isActive() {
    return active;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public void setMessageTemplate(String messageTemplate) {
    this.messageTemplate = messageTemplate;
  }

  public void setConfigJson(Map<String, Object> configJson) {
    this.configJson = configJson != null ? configJson : new LinkedHashMap<>();
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
