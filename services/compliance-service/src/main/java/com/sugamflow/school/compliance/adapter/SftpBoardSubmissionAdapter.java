package com.sugamflow.school.compliance.adapter;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sugamflow.school.compliance.persistence.entity.ExportArtifactEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;

/**
 * SFTP board upload stub. Queues a job record; real SFTP transfer lands when CBSE/state publishes
 * credentials/endpoints. Never claims a live government upload succeeded.
 */
@Component
public class SftpBoardSubmissionAdapter implements BoardSubmissionAdapter {
  private final boolean enabled;
  private final String host;

  public SftpBoardSubmissionAdapter(
      @Value("${compliance.adapters.sftp.enabled:false}") boolean enabled,
      @Value("${compliance.adapters.sftp.host:}") String host) {
    this.enabled = enabled;
    this.host = host == null ? "" : host.trim();
  }

  @Override
  public String channel() {
    return "SFTP";
  }

  @Override
  public AdapterResult submit(
      SubmissionCampaignEntity campaign, List<ExportArtifactEntity> artifacts, String note) {
    int count = artifacts == null ? 0 : artifacts.size();
    if (!enabled || host.isBlank()) {
      return new AdapterResult(
          true,
          "SFTP adapter stub accepted job (not connected). Configure compliance.adapters.sftp.*"
              + " when a board endpoint is available. Artifacts staged: "
              + count
              + ".",
          "SFTP-STUB-" + campaign.getId());
    }
    return new AdapterResult(
        true,
        "SFTP adapter stub queued transfer to "
            + host
            + " for "
            + count
            + " artifact(s). Live upload not implemented yet.",
        "SFTP-QUEUED-" + campaign.getId());
  }
}
