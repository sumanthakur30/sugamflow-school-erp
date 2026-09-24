package com.sugamflow.school.compliance.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.dto.ComplianceDashboardResponse;
import com.sugamflow.school.compliance.dto.ComplianceProfileRequest;
import com.sugamflow.school.compliance.dto.ComplianceProfileResponse;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.persistence.entity.SchoolComplianceProfileEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;
import com.sugamflow.school.compliance.persistence.repo.SchoolComplianceProfileRepository;
import com.sugamflow.school.compliance.persistence.repo.SubmissionCampaignRepository;
import com.sugamflow.school.compliance.persistence.repo.ValidationFindingRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class ComplianceService {
  public static final String FEATURE_CBSE_COMPLIANCE = "FEATURE_CBSE_COMPLIANCE";

  private final SchoolComplianceProfileRepository profileRepository;
  private final SubmissionCampaignRepository campaignRepository;
  private final ValidationFindingRepository findingRepository;
  private final DocumentVaultService documentVaultService;
  private final InfrastructureService infrastructureService;
  private final ConfigEngineClient configEngineClient;
  private final BoardPackService boardPackService;

  public ComplianceService(
      SchoolComplianceProfileRepository profileRepository,
      SubmissionCampaignRepository campaignRepository,
      ValidationFindingRepository findingRepository,
      DocumentVaultService documentVaultService,
      InfrastructureService infrastructureService,
      ConfigEngineClient configEngineClient,
      BoardPackService boardPackService) {
    this.profileRepository = profileRepository;
    this.campaignRepository = campaignRepository;
    this.findingRepository = findingRepository;
    this.documentVaultService = documentVaultService;
    this.infrastructureService = infrastructureService;
    this.configEngineClient = configEngineClient;
    this.boardPackService = boardPackService;
  }

  @Transactional(readOnly = true)
  public ComplianceDashboardResponse dashboard() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SchoolComplianceProfileEntity profile =
        profileRepository.findByOrganizationId(scope.organizationId()).orElse(null);
    int completeness = profileCompleteness(profile);
    List<String> missing = missingProfileFields(profile);

    List<SubmissionCampaignEntity> campaigns =
        campaignRepository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId());
    long pending =
        campaigns.stream()
            .filter(c -> !"SUBMITTED".equalsIgnoreCase(c.getStatus()) && !"ARCHIVED".equalsIgnoreCase(c.getStatus()))
            .count();
    long submitted =
        campaignRepository.countByOrganizationIdAndStatus(scope.organizationId(), "SUBMITTED");
    Instant now = Instant.now();
    long overdue =
        campaigns.stream()
            .filter(
                c ->
                    c.getDueAt() != null
                        && c.getDueAt().isBefore(now)
                        && !"SUBMITTED".equalsIgnoreCase(c.getStatus())
                        && !"ARCHIVED".equalsIgnoreCase(c.getStatus()))
            .count();

    List<String> actions = new ArrayList<>();
    if (profile == null) {
      actions.add("Create school compliance profile (affiliation, UDISE+, principal).");
    } else if (completeness < 80) {
      actions.add("Complete school profile — " + missing.size() + " required field(s) missing.");
    }
    long openBlockers =
        findingRepository.countByOrganizationIdAndStatusAndSeverity(
            scope.organizationId(), "OPEN", "BLOCKER");
    long openWarns =
        findingRepository.countByOrganizationIdAndStatusAndSeverity(
            scope.organizationId(), "OPEN", "WARN");
    if (openBlockers > 0) {
      actions.add(openBlockers + " data blocker(s) — fix in Data Readiness before submission.");
      actions.add("Use Import Center to gap-fill blank student/staff fields from Excel/CSV.");
    } else if (openWarns > 0) {
      actions.add(openWarns + " data warning(s) to review in Data Readiness.");
    } else if (campaigns.isEmpty()) {
      actions.add("Run master data validation from Data Readiness to create a campaign.");
    }
    if (pending == 0 && !campaigns.isEmpty() && openBlockers == 0) {
      actions.add("Use Campaign Workspace to approve, lock, export, and submit.");
    }
    if (overdue > 0) {
      actions.add(overdue + " campaign(s) past due date.");
    }
    long docAlerts = documentVaultService.countExpiringOrExpired(scope.organizationId());
    if (docAlerts > 0) {
      actions.add(docAlerts + " compliance document(s) expiring or expired — check Documents Vault.");
    }
    long missingDocs = documentVaultService.countMissingRecommended(scope.organizationId());
    if (missingDocs > 0) {
      actions.add(missingDocs + " recommended CBSE document type(s) missing from vault.");
    }
    var infra = infrastructureService.summary();
    if (!infra.missingRecommendedCategories().isEmpty()) {
      actions.add(
          infra.missingRecommendedCategories().size()
              + " recommended infrastructure categor"
              + (infra.missingRecommendedCategories().size() == 1 ? "y" : "ies")
              + " not inventoried.");
    }
    if (actions.isEmpty()) {
      actions.add("Ready to publish mandatory disclosure from Disclosure Preview.");
    }

    int score = completeness;
    if (openBlockers > 0) {
      score = Math.max(0, score - 25);
    } else if (openWarns > 0) {
      score = Math.max(0, score - 10);
    }
    if (overdue > 0) {
      score = Math.max(0, score - 20);
    }
    if (docAlerts > 0) {
      score = Math.max(0, score - 10);
    }

    List<ComplianceDashboardResponse.CampaignSummary> recent =
        campaigns.stream().limit(5).map(this::toSummary).toList();

    return new ComplianceDashboardResponse(
        score,
        pending,
        submitted,
        overdue,
        completeness,
        missing.size(),
        actions,
        recent);
  }

  @Transactional(readOnly = true)
  public ComplianceProfileResponse getProfile() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SchoolComplianceProfileEntity profile =
        profileRepository
            .findByOrganizationId(scope.organizationId())
            .orElseGet(() -> emptyProfile(scope.organizationId()));
    return ComplianceProfileResponse.from(profile, profileCompleteness(profile.getId() == null ? null : profile));
  }

  @Transactional
  public ComplianceProfileResponse upsertProfile(ComplianceProfileRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SchoolComplianceProfileEntity profile =
        profileRepository
            .findByOrganizationId(scope.organizationId())
            .orElseGet(
                () -> {
                  SchoolComplianceProfileEntity created = new SchoolComplianceProfileEntity();
                  created.setOrganizationId(scope.organizationId());
                  return created;
                });
    apply(profile, request);
    profile = profileRepository.save(profile);
    return ComplianceProfileResponse.from(profile, profileCompleteness(profile));
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private void apply(SchoolComplianceProfileEntity profile, ComplianceProfileRequest request) {
    boardPackService.applyBoardPack(profile, request.boardCode(), request.activePackKey());
    profile.setSchoolName(trimToNull(request.schoolName()));
    profile.setAffiliationNumber(trimToNull(request.affiliationNumber()));
    profile.setSchoolCode(trimToNull(request.schoolCode()));
    profile.setUdisePlus(trimToNull(request.udisePlus()));
    profile.setDiseCode(trimToNull(request.diseCode()));
    profile.setAddressLine(trimToNull(request.addressLine()));
    profile.setCity(trimToNull(request.city()));
    profile.setStateCode(trimToNull(request.stateCode()));
    profile.setPincode(trimToNull(request.pincode()));
    profile.setPrincipalName(trimToNull(request.principalName()));
    profile.setPrincipalMobile(trimToNull(request.principalMobile()));
    profile.setPrincipalEmail(trimToNull(request.principalEmail()));
    profile.setSchoolPhone(trimToNull(request.schoolPhone()));
    profile.setSchoolEmail(trimToNull(request.schoolEmail()));
    profile.setBankAccountName(trimToNull(request.bankAccountName()));
    profile.setBankAccountNumber(trimToNull(request.bankAccountNumber()));
    profile.setBankIfsc(trimToNull(request.bankIfsc()));
    profile.setTrustSocietyName(trimToNull(request.trustSocietyName()));
    profile.setRecognitionDetails(trimToNull(request.recognitionDetails()));
    profile.setInfrastructureNotes(trimToNull(request.infrastructureNotes()));
  }

  private SchoolComplianceProfileEntity emptyProfile(String organizationId) {
    SchoolComplianceProfileEntity e = new SchoolComplianceProfileEntity();
    e.setOrganizationId(organizationId);
    e.setBoardCode("CBSE");
    e.setActivePackKey(boardPackService.defaultPackKey("CBSE"));
    return e;
  }

  private ComplianceDashboardResponse.CampaignSummary toSummary(SubmissionCampaignEntity c) {
    return new ComplianceDashboardResponse.CampaignSummary(
        c.getId(),
        c.getTitle(),
        c.getBoardCode(),
        c.getStatus(),
        c.getDueAt(),
        c.getComplianceScore() != null ? c.getComplianceScore() : BigDecimal.ZERO,
        c.getBlockerCount(),
        c.getWarnCount());
  }

  static int profileCompleteness(SchoolComplianceProfileEntity profile) {
    if (profile == null || profile.getId() == null) {
      return 0;
    }
    String[] values = {
      profile.getSchoolName(),
      profile.getAffiliationNumber(),
      profile.getSchoolCode(),
      profile.getUdisePlus(),
      profile.getAddressLine(),
      profile.getPrincipalName(),
      profile.getPrincipalMobile(),
      profile.getPrincipalEmail(),
      profile.getSchoolEmail(),
      profile.getTrustSocietyName()
    };
    int filled = 0;
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        filled++;
      }
    }
    return (int) Math.round((filled * 100.0) / values.length);
  }

  static List<String> missingProfileFields(SchoolComplianceProfileEntity profile) {
    List<String> missing = new ArrayList<>();
    if (profile == null || profile.getId() == null) {
      missing.add("schoolName");
      missing.add("affiliationNumber");
      missing.add("schoolCode");
      missing.add("udisePlus");
      missing.add("principalName");
      return missing;
    }
    if (blank(profile.getSchoolName())) missing.add("schoolName");
    if (blank(profile.getAffiliationNumber())) missing.add("affiliationNumber");
    if (blank(profile.getSchoolCode())) missing.add("schoolCode");
    if (blank(profile.getUdisePlus())) missing.add("udisePlus");
    if (blank(profile.getAddressLine())) missing.add("addressLine");
    if (blank(profile.getPrincipalName())) missing.add("principalName");
    if (blank(profile.getPrincipalMobile())) missing.add("principalMobile");
    if (blank(profile.getPrincipalEmail())) missing.add("principalEmail");
    if (blank(profile.getSchoolEmail())) missing.add("schoolEmail");
    if (blank(profile.getTrustSocietyName())) missing.add("trustSocietyName");
    return missing;
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
