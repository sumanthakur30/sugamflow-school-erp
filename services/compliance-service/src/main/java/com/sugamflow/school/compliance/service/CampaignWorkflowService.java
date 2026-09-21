package com.sugamflow.school.compliance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.adapter.BoardSubmissionAdapter;
import com.sugamflow.school.compliance.dto.ApprovalDecisionRequest;
import com.sugamflow.school.compliance.dto.CampaignResponse;
import com.sugamflow.school.compliance.dto.CreateCampaignRequest;
import com.sugamflow.school.compliance.dto.ExportRequest;
import com.sugamflow.school.compliance.dto.SubmitCampaignRequest;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.persistence.entity.ApprovalStepEntity;
import com.sugamflow.school.compliance.persistence.entity.ExportArtifactEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;
import com.sugamflow.school.compliance.persistence.repo.ApprovalStepRepository;
import com.sugamflow.school.compliance.persistence.repo.ExportArtifactRepository;
import com.sugamflow.school.compliance.persistence.repo.SubmissionCampaignRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class CampaignWorkflowService {
  private static final Set<String> TERMINAL_OR_LOCKED =
      Set.of("LOCKED", "EXPORTED", "SUBMITTED", "ARCHIVED");

  private final SubmissionCampaignRepository campaignRepository;
  private final ApprovalStepRepository approvalStepRepository;
  private final ExportArtifactRepository artifactRepository;
  private final ExportGeneratorService exportGeneratorService;
  private final ConfigEngineClient configEngineClient;
  private final Map<String, BoardSubmissionAdapter> adapters;
  private final com.sugamflow.school.compliance.persistence.repo.AdapterJobRepository adapterJobRepository;
  private final BoardPackService boardPackService;

  public CampaignWorkflowService(
      SubmissionCampaignRepository campaignRepository,
      ApprovalStepRepository approvalStepRepository,
      ExportArtifactRepository artifactRepository,
      ExportGeneratorService exportGeneratorService,
      ConfigEngineClient configEngineClient,
      List<BoardSubmissionAdapter> adapterList,
      com.sugamflow.school.compliance.persistence.repo.AdapterJobRepository adapterJobRepository,
      BoardPackService boardPackService) {
    this.campaignRepository = campaignRepository;
    this.approvalStepRepository = approvalStepRepository;
    this.artifactRepository = artifactRepository;
    this.exportGeneratorService = exportGeneratorService;
    this.configEngineClient = configEngineClient;
    this.adapters =
        adapterList.stream()
            .collect(Collectors.toMap(a -> a.channel().toUpperCase(Locale.ROOT), Function.identity()));
    this.adapterJobRepository = adapterJobRepository;
    this.boardPackService = boardPackService;
  }

  @Transactional(readOnly = true)
  public List<CampaignResponse> list() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return campaignRepository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId()).stream()
        .map(c -> toResponse(c, false))
        .toList();
  }

  @Transactional(readOnly = true)
  public CampaignResponse get(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return toResponse(requireCampaign(scope, id), true);
  }

  @Transactional
  public CampaignResponse create(CreateCampaignRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    BoardPackService.PackSelection pack =
        (request.boardCode() != null && !request.boardCode().isBlank())
                || (request.packKey() != null && !request.packKey().isBlank())
            ? boardPackService.resolveSelection(request.boardCode(), request.packKey())
            : boardPackService.resolveForOrganization(scope.organizationId());
    SubmissionCampaignEntity c = new SubmissionCampaignEntity();
    c.setOrganizationId(scope.organizationId());
    c.setTitle(request.title().trim());
    c.setBoardCode(pack.boardCode());
    c.setPackKey(pack.packKey());
    if (request.academicSessionId() != null && !request.academicSessionId().isBlank()) {
      c.setAcademicSessionId(request.academicSessionId().trim());
    } else if (scope.academicSessionId() != null && !scope.academicSessionId().isBlank()) {
      c.setAcademicSessionId(scope.academicSessionId());
    }
    c.setRequireManagementApproval(Boolean.TRUE.equals(request.requireManagementApproval()));
    c.setStatus("DRAFT");
    return toResponse(campaignRepository.save(c), true);
  }

  @Transactional
  public CampaignResponse submitForReview(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SubmissionCampaignEntity campaign = requireCampaign(scope, id);
    assertMutable(campaign);
    if (!Set.of("READY", "NEEDS_FIX", "DRAFT", "REJECTED").contains(campaign.getStatus().toUpperCase(Locale.ROOT))) {
      throw new ComplianceException(
          "INVALID_STATE",
          "Campaign must be READY/NEEDS_FIX/DRAFT/REJECTED to submit for review (status="
              + campaign.getStatus()
              + ")",
          HttpStatus.CONFLICT);
    }
    if (campaign.getBlockerCount() > 0) {
      throw new ComplianceException(
          "BLOCKERS_OPEN",
          "Resolve " + campaign.getBlockerCount() + " blocker(s) in Data Readiness before review.",
          HttpStatus.CONFLICT);
    }
    ensurePendingStep(campaign, "PRINCIPAL");
    if (campaign.isRequireManagementApproval()) {
      ensurePendingStep(campaign, "MANAGEMENT");
    }
    campaign.setStatus("PRINCIPAL_REVIEW");
    return toResponse(campaignRepository.save(campaign), true);
  }

  @Transactional
  public CampaignResponse decide(Long id, ApprovalDecisionRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SubmissionCampaignEntity campaign = requireCampaign(scope, id);
    String step = request.stepCode().trim().toUpperCase(Locale.ROOT);
    String decision = request.decision().trim().toUpperCase(Locale.ROOT);
    if (!Set.of("APPROVED", "REJECTED").contains(decision)) {
      throw new ComplianceException(
          "BAD_DECISION", "decision must be APPROVED or REJECTED", HttpStatus.BAD_REQUEST);
    }
    if (!Set.of("PRINCIPAL", "MANAGEMENT").contains(step)) {
      throw new ComplianceException(
          "BAD_STEP", "stepCode must be PRINCIPAL or MANAGEMENT", HttpStatus.BAD_REQUEST);
    }

    String status = campaign.getStatus().toUpperCase(Locale.ROOT);
    if ("PRINCIPAL".equals(step) && !"PRINCIPAL_REVIEW".equals(status)) {
      throw new ComplianceException(
          "INVALID_STATE", "Principal decision requires PRINCIPAL_REVIEW status", HttpStatus.CONFLICT);
    }
    if ("MANAGEMENT".equals(step) && !"MANAGEMENT_REVIEW".equals(status)) {
      throw new ComplianceException(
          "INVALID_STATE",
          "Management decision requires MANAGEMENT_REVIEW status",
          HttpStatus.CONFLICT);
    }

    ApprovalStepEntity approval =
        approvalStepRepository
            .findByCampaignIdAndStepCode(campaign.getId(), step)
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "STEP_MISSING", "Approval step " + step + " not found", HttpStatus.NOT_FOUND));
    if (!"PENDING".equalsIgnoreCase(approval.getDecision())) {
      throw new ComplianceException(
          "ALREADY_DECIDED", "Step " + step + " already decided", HttpStatus.CONFLICT);
    }
    approval.setDecision(decision);
    approval.setActorUserId(scope.userId());
    approval.setCommentText(trimToNull(request.comment()));
    approval.setDecidedAt(Instant.now());
    approvalStepRepository.save(approval);

    if ("REJECTED".equals(decision)) {
      campaign.setStatus("REJECTED");
      return toResponse(campaignRepository.save(campaign), true);
    }

    if ("PRINCIPAL".equals(step)) {
      if (campaign.isRequireManagementApproval()) {
        campaign.setStatus("MANAGEMENT_REVIEW");
      } else {
        campaign.setStatus("APPROVED");
      }
    } else {
      campaign.setStatus("APPROVED");
    }
    return toResponse(campaignRepository.save(campaign), true);
  }

  @Transactional
  public CampaignResponse lock(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SubmissionCampaignEntity campaign = requireCampaign(scope, id);
    if (!"APPROVED".equalsIgnoreCase(campaign.getStatus())
        && !"READY".equalsIgnoreCase(campaign.getStatus())) {
      throw new ComplianceException(
          "INVALID_STATE",
          "Lock requires APPROVED (or READY when approvals skipped) — status=" + campaign.getStatus(),
          HttpStatus.CONFLICT);
    }
    if (campaign.getBlockerCount() > 0) {
      throw new ComplianceException(
          "BLOCKERS_OPEN", "Cannot lock with open blockers", HttpStatus.CONFLICT);
    }
    if ("READY".equalsIgnoreCase(campaign.getStatus())) {
      // allow principal-less lock only when no approval trail was started
      List<ApprovalStepEntity> steps =
          approvalStepRepository.findByCampaignIdOrderByCreatedAtAsc(campaign.getId());
      if (!steps.isEmpty()) {
        throw new ComplianceException(
            "APPROVAL_REQUIRED",
            "Complete principal approval before lock",
            HttpStatus.CONFLICT);
      }
    } else {
      requireApproved(campaign, "PRINCIPAL");
      if (campaign.isRequireManagementApproval()) {
        requireApproved(campaign, "MANAGEMENT");
      }
    }
    campaign.setStatus("LOCKED");
    campaign.setLockedAt(Instant.now());
    campaign.setLockedBy(scope.userId());
    return toResponse(campaignRepository.save(campaign), true);
  }

  @Transactional
  public CampaignResponse export(Long id, ExportRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SubmissionCampaignEntity campaign = requireCampaign(scope, id);
    if (!Set.of("LOCKED", "EXPORTED").contains(campaign.getStatus().toUpperCase(Locale.ROOT))) {
      throw new ComplianceException(
          "INVALID_STATE",
          "Export requires LOCKED (or re-export EXPORTED) — status=" + campaign.getStatus(),
          HttpStatus.CONFLICT);
    }
    Set<String> formats = new HashSet<>();
    if (request != null && request.formats() != null) {
      formats.addAll(request.formats());
    }
    List<CampaignResponse.ExportArtifactResponse> artifacts =
        exportGeneratorService.generate(scope, campaign, formats);
    campaign.setStatus("EXPORTED");
    CampaignResponse response = toResponse(campaignRepository.save(campaign), true);
    // artifacts already loaded in toResponse; ensure latest batch visible
    if (response.artifacts() == null || response.artifacts().isEmpty()) {
      return new CampaignResponse(
          response.id(),
          response.organizationId(),
          response.academicSessionId(),
          response.boardCode(),
          response.packKey(),
          response.title(),
          response.status(),
          response.dueAt(),
          response.complianceScore(),
          response.blockerCount(),
          response.warnCount(),
          response.requireManagementApproval(),
          response.lockedAt(),
          response.lockedBy(),
          response.submittedAt(),
          response.submittedBy(),
          response.archivedAt(),
          response.archivedBy(),
          response.adapterChannel(),
          response.createdAt(),
          response.updatedAt(),
          response.approvals(),
          artifacts);
    }
    return response;
  }

  @Transactional
  public CampaignResponse submit(Long id, SubmitCampaignRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SubmissionCampaignEntity campaign = requireCampaign(scope, id);
    if (!Set.of("EXPORTED", "LOCKED").contains(campaign.getStatus().toUpperCase(Locale.ROOT))) {
      throw new ComplianceException(
          "INVALID_STATE",
          "Submit requires EXPORTED (or LOCKED with prior artifacts) — status=" + campaign.getStatus(),
          HttpStatus.CONFLICT);
    }
    List<ExportArtifactEntity> artifacts =
        artifactRepository.findByCampaignIdOrderByCreatedAtDesc(campaign.getId());
    if (artifacts.isEmpty()) {
      throw new ComplianceException(
          "NO_ARTIFACTS", "Generate export artifacts before submit", HttpStatus.CONFLICT);
    }
    String channel =
        request != null && request.channel() != null && !request.channel().isBlank()
            ? request.channel().trim().toUpperCase(Locale.ROOT)
            : "FILE";
    BoardSubmissionAdapter adapter = adapters.get(channel);
    if (adapter == null) {
      throw new ComplianceException(
          "UNKNOWN_CHANNEL",
          "Unsupported adapter channel: " + channel + " (available: " + adapters.keySet() + ")",
          HttpStatus.BAD_REQUEST);
    }
    String note = request == null ? null : request.note();
    BoardSubmissionAdapter.AdapterResult result = adapter.submit(campaign, artifacts, note);
    if (!result.success()) {
      throw new ComplianceException("SUBMIT_FAILED", result.message(), HttpStatus.BAD_GATEWAY);
    }

    com.sugamflow.school.compliance.persistence.entity.AdapterJobEntity job =
        new com.sugamflow.school.compliance.persistence.entity.AdapterJobEntity();
    job.setOrganizationId(scope.organizationId());
    job.setCampaignId(campaign.getId());
    job.setBoardCode(campaign.getBoardCode());
    job.setChannel(channel);
    job.setStatus(channel.equals("FILE") ? "COMPLETED" : "STUBBED");
    job.setExternalRef(result.externalRef());
    job.setRequestNote(note);
    job.setResultMessage(result.message());
    job.setArtifactCount(artifacts.size());
    job.setCreatedBy(scope.userId());
    job.setCompletedAt(Instant.now());
    adapterJobRepository.save(job);

    campaign.setStatus("SUBMITTED");
    campaign.setSubmittedAt(Instant.now());
    campaign.setSubmittedBy(scope.userId());
    campaign.setAdapterChannel(channel);
    return toResponse(campaignRepository.save(campaign), true);
  }

  @Transactional
  public CampaignResponse archive(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SubmissionCampaignEntity campaign = requireCampaign(scope, id);
    if (!Set.of("SUBMITTED", "EXPORTED", "LOCKED", "REJECTED", "APPROVED")
        .contains(campaign.getStatus().toUpperCase(Locale.ROOT))) {
      throw new ComplianceException(
          "INVALID_STATE",
          "Cannot archive campaign in status " + campaign.getStatus(),
          HttpStatus.CONFLICT);
    }
    campaign.setStatus("ARCHIVED");
    campaign.setArchivedAt(Instant.now());
    campaign.setArchivedBy(scope.userId());
    return toResponse(campaignRepository.save(campaign), true);
  }

  @Transactional(readOnly = true)
  public FilePayload downloadArtifact(Long campaignId, Long artifactId) throws IOException {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requireCampaign(scope, campaignId);
    ExportArtifactEntity artifact =
        artifactRepository
            .findByIdAndOrganizationId(artifactId, scope.organizationId())
            .filter(a -> campaignId.equals(a.getCampaignId()))
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "Export artifact not found", HttpStatus.NOT_FOUND));
    byte[] bytes = Files.readAllBytes(exportGeneratorService.resolveArtifactPath(artifact));
    String contentType =
        artifact.getContentType() == null || artifact.getContentType().isBlank()
            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
            : artifact.getContentType();
    return new FilePayload(artifact.getFileName(), contentType, new ByteArrayResource(bytes));
  }

  public static boolean isImmutableStatus(String status) {
    return status != null && TERMINAL_OR_LOCKED.contains(status.toUpperCase(Locale.ROOT));
  }

  private void ensurePendingStep(SubmissionCampaignEntity campaign, String stepCode) {
    approvalStepRepository
        .findByCampaignIdAndStepCode(campaign.getId(), stepCode)
        .orElseGet(
            () -> {
              ApprovalStepEntity step = new ApprovalStepEntity();
              step.setOrganizationId(campaign.getOrganizationId());
              step.setCampaignId(campaign.getId());
              step.setStepCode(stepCode);
              step.setDecision("PENDING");
              return approvalStepRepository.save(step);
            });
  }

  private void requireApproved(SubmissionCampaignEntity campaign, String stepCode) {
    ApprovalStepEntity step =
        approvalStepRepository
            .findByCampaignIdAndStepCode(campaign.getId(), stepCode)
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "APPROVAL_REQUIRED",
                        stepCode + " approval is required",
                        HttpStatus.CONFLICT));
    if (!"APPROVED".equalsIgnoreCase(step.getDecision())) {
      throw new ComplianceException(
          "APPROVAL_REQUIRED",
          stepCode + " must be APPROVED before lock",
          HttpStatus.CONFLICT);
    }
  }

  private void assertMutable(SubmissionCampaignEntity campaign) {
    if (isImmutableStatus(campaign.getStatus())) {
      throw new ComplianceException(
          "LOCKED",
          "Campaign is " + campaign.getStatus() + " and cannot change workflow stage this way",
          HttpStatus.CONFLICT);
    }
  }

  private SubmissionCampaignEntity requireCampaign(TenantScope scope, Long id) {
    return campaignRepository
        .findByIdAndOrganizationId(id, scope.organizationId())
        .orElseThrow(
            () -> new ComplianceException("NOT_FOUND", "Campaign not found", HttpStatus.NOT_FOUND));
  }

  private CampaignResponse toResponse(SubmissionCampaignEntity c, boolean detail) {
    List<CampaignResponse.ApprovalStepResponse> approvals = List.of();
    List<CampaignResponse.ExportArtifactResponse> artifacts = List.of();
    if (detail) {
      approvals =
          approvalStepRepository.findByCampaignIdOrderByCreatedAtAsc(c.getId()).stream()
              .map(
                  s ->
                      new CampaignResponse.ApprovalStepResponse(
                          s.getId(),
                          s.getStepCode(),
                          s.getDecision(),
                          s.getActorUserId(),
                          s.getCommentText(),
                          s.getDecidedAt(),
                          s.getCreatedAt()))
              .toList();
      artifacts =
          artifactRepository.findByCampaignIdOrderByCreatedAtDesc(c.getId()).stream()
              .map(
                  a ->
                      new CampaignResponse.ExportArtifactResponse(
                          a.getId(),
                          a.getArtifactKey(),
                          a.getFormatCode(),
                          a.getFileName(),
                          a.getContentType(),
                          a.getFileSize(),
                          a.getChecksumSha256(),
                          a.getCreatedAt()))
              .toList();
    }
    return new CampaignResponse(
        c.getId(),
        c.getOrganizationId(),
        c.getAcademicSessionId(),
        c.getBoardCode(),
        c.getPackKey(),
        c.getTitle(),
        c.getStatus(),
        c.getDueAt(),
        c.getComplianceScore(),
        c.getBlockerCount(),
        c.getWarnCount(),
        c.isRequireManagementApproval(),
        c.getLockedAt(),
        c.getLockedBy(),
        c.getSubmittedAt(),
        c.getSubmittedBy(),
        c.getArchivedAt(),
        c.getArchivedBy(),
        c.getAdapterChannel(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        approvals,
        artifacts);
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  public record FilePayload(String fileName, String contentType, Resource resource) {}
}
