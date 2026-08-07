package com.sugamflow.school.compliance.adapter;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sugamflow.school.compliance.persistence.entity.ExportArtifactEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;

/**
 * REST/OAuth board upload stub. Records intent only until an official board API exists.
 */
@Component
public class RestBoardSubmissionAdapter implements BoardSubmissionAdapter {
  private final boolean enabled;
  private final String endpoint;

  public RestBoardSubmissionAdapter(
      @Value("${compliance.adapters.rest.enabled:false}") boolean enabled,
      @Value("${compliance.adapters.rest.endpoint:}") String endpoint) {
    this.enabled = enabled;
    this.endpoint = endpoint == null ? "" : endpoint.trim();
  }

  @Override
  public String channel() {
    return "REST";
  }

  @Override
  public AdapterResult submit(
      SubmissionCampaignEntity campaign, List<ExportArtifactEntity> artifacts, String note) {
    int count = artifacts == null ? 0 : artifacts.size();
    if (!enabled || endpoint.isBlank()) {
      return new AdapterResult(
          true,
          "REST adapter stub accepted job (no board API configured). Set compliance.adapters.rest.*"
              + " when available. Artifacts staged: "
              + count
              + ".",
          "REST-STUB-" + campaign.getId());
    }
    return new AdapterResult(
        true,
        "REST adapter stub queued POST to "
            + endpoint
            + " for "
            + count
            + " artifact(s). Live OAuth upload not implemented yet.",
        "REST-QUEUED-" + campaign.getId());
  }
}
