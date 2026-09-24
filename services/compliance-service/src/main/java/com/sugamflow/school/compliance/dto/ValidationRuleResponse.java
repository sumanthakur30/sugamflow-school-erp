package com.sugamflow.school.compliance.dto;

import java.util.Map;

import com.sugamflow.school.compliance.persistence.entity.ValidationRuleEntity;

public record ValidationRuleResponse(
    Long id,
    String boardCode,
    String ruleCode,
    String entityType,
    String ruleType,
    Map<String, Object> configJson,
    String severity,
    String messageTemplate,
    boolean active) {

  public static ValidationRuleResponse from(ValidationRuleEntity e) {
    return new ValidationRuleResponse(
        e.getId(),
        e.getBoardCode(),
        e.getRuleCode(),
        e.getEntityType(),
        e.getRuleType(),
        e.getConfigJson(),
        e.getSeverity(),
        e.getMessageTemplate(),
        e.isActive());
  }
}
