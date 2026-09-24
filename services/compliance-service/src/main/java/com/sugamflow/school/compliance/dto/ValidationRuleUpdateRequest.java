package com.sugamflow.school.compliance.dto;

import java.util.Map;

import jakarta.validation.constraints.Size;

public record ValidationRuleUpdateRequest(
    @Size(max = 20) String severity,
    @Size(max = 500) String messageTemplate,
    Map<String, Object> configJson,
    Boolean active) {}
