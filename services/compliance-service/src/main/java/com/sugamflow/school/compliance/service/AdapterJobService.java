package com.sugamflow.school.compliance.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.dto.AdapterJobResponse;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.persistence.entity.AdapterJobEntity;
import com.sugamflow.school.compliance.persistence.repo.AdapterJobRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class AdapterJobService {
  private final AdapterJobRepository repository;
  private final ConfigEngineClient configEngineClient;

  public AdapterJobService(
      AdapterJobRepository repository, ConfigEngineClient configEngineClient) {
    this.repository = repository;
    this.configEngineClient = configEngineClient;
  }

  @Transactional(readOnly = true)
  public List<AdapterJobResponse> list(Long campaignId) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    List<AdapterJobEntity> rows =
        campaignId == null
            ? repository.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId())
            : repository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
    return rows.stream()
        .filter(j -> scope.organizationId().equals(j.getOrganizationId()))
        .limit(50)
        .map(this::toResponse)
        .toList();
  }

  private AdapterJobResponse toResponse(AdapterJobEntity j) {
    return new AdapterJobResponse(
        j.getId(),
        j.getCampaignId(),
        j.getBoardCode(),
        j.getChannel(),
        j.getStatus(),
        j.getExternalRef(),
        j.getRequestNote(),
        j.getResultMessage(),
        j.getArtifactCount(),
        j.getCreatedBy(),
        j.getCreatedAt(),
        j.getCompletedAt());
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }
}
