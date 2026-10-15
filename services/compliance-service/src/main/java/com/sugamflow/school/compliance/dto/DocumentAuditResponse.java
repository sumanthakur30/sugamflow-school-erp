package com.sugamflow.school.compliance.dto;

import java.time.Instant;

public record DocumentAuditResponse(
    Long id, String action, String actorUserId, String detail, Instant createdAt) {}
