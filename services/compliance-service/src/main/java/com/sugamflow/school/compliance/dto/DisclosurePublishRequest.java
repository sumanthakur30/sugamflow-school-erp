package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.Size;

public record DisclosurePublishRequest(
    @Size(max = 160) String slug,
    @Size(max = 255) String title,
    Boolean publishNow) {}
