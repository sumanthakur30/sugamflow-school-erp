package com.sugamflow.school.compliance.adapter;

import java.util.List;

import com.sugamflow.school.compliance.persistence.entity.ExportArtifactEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;

/** SPI for board submission channels. P3 ships FILE (manual portal upload). */
public interface BoardSubmissionAdapter {
  String channel();

  AdapterResult submit(SubmissionCampaignEntity campaign, List<ExportArtifactEntity> artifacts, String note);

  record AdapterResult(boolean success, String message, String externalRef) {}
}
