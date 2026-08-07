package com.sugamflow.school.compliance.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sugamflow.school.compliance.persistence.entity.ExportArtifactEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;

@Component
public class FileBoardSubmissionAdapter implements BoardSubmissionAdapter {
  @Override
  public String channel() {
    return "FILE";
  }

  @Override
  public AdapterResult submit(
      SubmissionCampaignEntity campaign, List<ExportArtifactEntity> artifacts, String note) {
    int count = artifacts == null ? 0 : artifacts.size();
    String msg =
        "Marked submitted for manual board portal upload ("
            + count
            + " artifact"
            + (count == 1 ? "" : "s")
            + ").";
    if (note != null && !note.isBlank()) {
      msg = msg + " Note: " + note.trim();
    }
    return new AdapterResult(true, msg, "FILE-" + campaign.getId());
  }
}
