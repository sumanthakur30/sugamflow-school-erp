package com.sugamflow.school.compliance.dto;

import jakarta.validation.constraints.Size;

public record SubmitCampaignRequest(@Size(max = 40) String channel, @Size(max = 2000) String note) {}
