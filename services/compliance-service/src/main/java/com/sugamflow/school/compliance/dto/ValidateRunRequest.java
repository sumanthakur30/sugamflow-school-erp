package com.sugamflow.school.compliance.dto;

import java.time.Instant;

public record ValidateRunRequest(
    String title, String boardCode, String packKey, String academicSessionId) {}
