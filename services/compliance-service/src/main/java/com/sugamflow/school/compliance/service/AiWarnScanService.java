package com.sugamflow.school.compliance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.config.ComplianceProperties;
import com.sugamflow.school.compliance.dto.AiScanJobResponse;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.integration.MasterDataClient;
import com.sugamflow.school.compliance.persistence.entity.AiScanJobEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;
import com.sugamflow.school.compliance.persistence.entity.ValidationFindingEntity;
import com.sugamflow.school.compliance.persistence.repo.AiScanJobRepository;
import com.sugamflow.school.compliance.persistence.repo.SubmissionCampaignRepository;
import com.sugamflow.school.compliance.persistence.repo.ValidationFindingRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

/**
 * Async AI WARN scanner. Heuristics always run; optional OpenAI-compatible LLM never blocks and
 * cannot emit BLOCKER severity.
 */
@Service
public class AiWarnScanService {
  private static final Logger log = LoggerFactory.getLogger(AiWarnScanService.class);
  public static final String SOURCE_AI = "AI";

  private final AiScanJobRepository jobRepository;
  private final SubmissionCampaignRepository campaignRepository;
  private final ValidationFindingRepository findingRepository;
  private final MasterDataClient masterDataClient;
  private final ConfigEngineClient configEngineClient;
  private final ComplianceProperties properties;
  private final RestClient.Builder restClientBuilder;
  private final ObjectMapper objectMapper;
  private final AiWarnScanRunner scanRunner;

  public AiWarnScanService(
      AiScanJobRepository jobRepository,
      SubmissionCampaignRepository campaignRepository,
      ValidationFindingRepository findingRepository,
      MasterDataClient masterDataClient,
      ConfigEngineClient configEngineClient,
      ComplianceProperties properties,
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @org.springframework.context.annotation.Lazy AiWarnScanRunner scanRunner) {
    this.jobRepository = jobRepository;
    this.campaignRepository = campaignRepository;
    this.findingRepository = findingRepository;
    this.masterDataClient = masterDataClient;
    this.configEngineClient = configEngineClient;
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
    this.objectMapper = objectMapper;
    this.scanRunner = scanRunner;
  }

  @Transactional
  public AiScanJobResponse start(Long campaignId) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SubmissionCampaignEntity campaign =
        campaignRepository
            .findByIdAndOrganizationId(campaignId, scope.organizationId())
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "Campaign not found", HttpStatus.NOT_FOUND));
    if (CampaignWorkflowService.isImmutableStatus(campaign.getStatus())
        && !"LOCKED".equalsIgnoreCase(campaign.getStatus())
        && !"EXPORTED".equalsIgnoreCase(campaign.getStatus())) {
      // allow AI scan on DRAFT/READY/etc and also LOCKED/EXPORTED for advisory only
    }
    if ("ARCHIVED".equalsIgnoreCase(campaign.getStatus())
        || "SUBMITTED".equalsIgnoreCase(campaign.getStatus())) {
      throw new ComplianceException(
          "INVALID_STATE",
          "AI scan is not available for " + campaign.getStatus() + " campaigns",
          HttpStatus.CONFLICT);
    }

    AiScanJobEntity job = new AiScanJobEntity();
    job.setOrganizationId(scope.organizationId());
    job.setCampaignId(campaign.getId());
    job.setStatus("QUEUED");
    job.setProvider("heuristic");
    job.setCreatedBy(scope.userId());
    job = jobRepository.save(job);
    scanRunner.run(job.getId(), copyScope(scope));
    return toResponse(job);
  }

  @Transactional(readOnly = true)
  public AiScanJobResponse get(Long jobId) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return toResponse(
        jobRepository
            .findByIdAndOrganizationId(jobId, scope.organizationId())
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "AI scan job not found", HttpStatus.NOT_FOUND)));
  }

  @Transactional(readOnly = true)
  public List<AiScanJobResponse> list(Long campaignId) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    List<AiScanJobEntity> rows =
        campaignId == null
            ? jobRepository.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId())
            : jobRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
    return rows.stream()
        .filter(j -> scope.organizationId().equals(j.getOrganizationId()))
        .limit(50)
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public void executeJob(Long jobId, TenantScope scope) {
    AiScanJobEntity job =
        jobRepository
            .findById(jobId)
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "AI scan job not found", HttpStatus.NOT_FOUND));
    job.setStatus("RUNNING");
    job.setStartedAt(Instant.now());
    jobRepository.save(job);

    SubmissionCampaignEntity campaign =
        campaignRepository
            .findByIdAndOrganizationId(job.getCampaignId(), scope.organizationId())
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "Campaign not found", HttpStatus.NOT_FOUND));

    findingRepository.deleteByCampaignIdAndSource(campaign.getId(), SOURCE_AI);

    List<Map<String, Object>> students = masterDataClient.listStudentProjections(scope);
    List<Map<String, Object>> staff = masterDataClient.listStaffProjections(scope);

    List<ValidationFindingEntity> findings = new ArrayList<>();
    findings.addAll(scanEntityType(scope, campaign, "STUDENT", students));
    findings.addAll(scanEntityType(scope, campaign, "STAFF", staff));

    boolean fallbackUsed = false;
    String provider = "heuristic";
    if (properties.getAi().isConfigured()) {
      try {
        String tip = callExternalTip(students.size(), staff.size(), findings.size());
        if (tip != null && !tip.isBlank()) {
          provider = "heuristic+llm";
          ValidationFindingEntity summary = baseFinding(scope, campaign, "CAMPAIGN", null, "Campaign");
          summary.setFieldKey("aiSummary");
          summary.setRuleCode("AI_LLM_SUMMARY");
          summary.setMessage(tip);
          summary.setSuggestion("Review AI advisory notes; they never block submission alone.");
          summary.setConfidence(new BigDecimal("0.55"));
          findings.add(summary);
        }
      } catch (Exception ex) {
        fallbackUsed = true;
        log.info("LLM tip skipped: {}", ex.getMessage());
      }
    }

    if (!findings.isEmpty()) {
      findingRepository.saveAll(findings);
    }

    // Soft-update campaign warn count (AI adds WARNs; do not wipe deterministic ones)
    int openWarns =
        (int)
            findingRepository.countByOrganizationIdAndStatusAndSeverity(
                scope.organizationId(), "OPEN", "WARN");
    campaign.setWarnCount(openWarns);
    campaignRepository.save(campaign);

    job.setProvider(provider);
    job.setFallbackUsed(fallbackUsed);
    job.setWarnCount(findings.size());
    job.setRecordsScanned(students.size() + staff.size());
    job.setStatus("COMPLETED");
    job.setResultMessage(
        "AI WARN scan complete: "
            + findings.size()
            + " advisory finding(s) on "
            + (students.size() + staff.size())
            + " records.");
    job.setCompletedAt(Instant.now());
    jobRepository.save(job);
  }

  private List<ValidationFindingEntity> scanEntityType(
      TenantScope scope,
      SubmissionCampaignEntity campaign,
      String entityType,
      List<Map<String, Object>> rows) {
    List<ValidationFindingEntity> out = new ArrayList<>();
    // missing photo / contact completeness
    for (Map<String, Object> row : rows) {
      String id = str(row.get("id"));
      String label = label(entityType, row);
      if ("STUDENT".equals(entityType)) {
        if (blank(row.get("photoUrl")) && blank(row.get("photo")) && blank(row.get("studentPhoto"))) {
          out.add(
              warn(
                  scope,
                  campaign,
                  entityType,
                  id,
                  label,
                  "photoUrl",
                  "AI_MISSING_PHOTO",
                  "Photo appears missing for " + label + ".",
                  "Add a student photo before board submission if required by the pack.",
                  "0.70"));
        }
      }
      String mobile = str(row.get("mobile")).replaceAll("\\D", "");
      if (!mobile.isEmpty() && (mobile.length() != 10 || !mobile.matches("[6-9]\\d{9}"))) {
        out.add(
            warn(
                scope,
                campaign,
                entityType,
                id,
                label,
                "mobile",
                "AI_MOBILE_OUTLIER",
                "Mobile looks unusual for " + label + " (" + str(row.get("mobile")) + ").",
                "Confirm the contact number with the family/staff record.",
                "0.60"));
      }
    }

    // fuzzy name duplicates
    for (int i = 0; i < rows.size(); i++) {
      Map<String, Object> a = rows.get(i);
      String nameA = normalizeName(str(a.get("fullName")));
      if (nameA.length() < 4) {
        continue;
      }
      for (int j = i + 1; j < rows.size(); j++) {
        Map<String, Object> b = rows.get(j);
        String nameB = normalizeName(str(b.get("fullName")));
        if (nameB.length() < 4) {
          continue;
        }
        int dist = levenshtein(nameA, nameB);
        boolean near = nameA.equals(nameB) || (Math.max(nameA.length(), nameB.length()) >= 6 && dist <= 2);
        if (!near) {
          continue;
        }
        String labelA = label(entityType, a);
        String labelB = label(entityType, b);
        out.add(
            warn(
                scope,
                campaign,
                entityType,
                str(a.get("id")),
                labelA,
                "fullName",
                "AI_FUZZY_DUPLICATE_NAME",
                "Possible duplicate name: \""
                    + str(a.get("fullName"))
                    + "\" ≈ \""
                    + str(b.get("fullName"))
                    + "\" ("
                    + labelB
                    + ").",
                "Review both records; AI warnings never block submission alone.",
                nameA.equals(nameB) ? "0.85" : "0.65"));
      }
    }
    return out;
  }

  private String callExternalTip(int students, int staff, int heuristicWarns) throws Exception {
    ComplianceProperties.Ai ai = properties.getAi();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", ai.getModel());
    payload.put(
        "messages",
        List.of(
            Map.of(
                "role",
                "system",
                "content",
                "You advise CBSE school compliance officers. Reply with ONE short advisory sentence. Never claim official board API success."),
            Map.of(
                "role",
                "user",
                "content",
                "Scanned "
                    + students
                    + " students and "
                    + staff
                    + " staff. Heuristic WARN count="
                    + heuristicWarns
                    + ". Give one pre-submit tip.")));
    payload.put("temperature", 0.2);
    String raw =
        restClientBuilder
            .build()
            .post()
            .uri(ai.getApiUrl())
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + ai.getApiKey())
            .retrieve()
            .body(String.class);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    JsonNode root = objectMapper.readTree(raw);
    JsonNode choices = root.path("choices");
    if (choices.isArray() && !choices.isEmpty()) {
      String content = choices.get(0).path("message").path("content").asText(null);
      if (content != null && content.length() > 400) {
        return content.substring(0, 400);
      }
      return content;
    }
    return null;
  }

  @Transactional
  public void markFailed(Long jobId, String message) {
    failJob(jobId, message);
  }

  private void failJob(Long jobId, String message) {
    jobRepository
        .findById(jobId)
        .ifPresent(
            job -> {
              job.setStatus("FAILED");
              job.setResultMessage(message);
              job.setCompletedAt(Instant.now());
              job.setFallbackUsed(true);
              jobRepository.save(job);
            });
  }

  private ValidationFindingEntity warn(
      TenantScope scope,
      SubmissionCampaignEntity campaign,
      String entityType,
      String entityId,
      String label,
      String field,
      String rule,
      String message,
      String suggestion,
      String confidence) {
    ValidationFindingEntity f = baseFinding(scope, campaign, entityType, entityId, label);
    f.setFieldKey(field);
    f.setRuleCode(rule);
    f.setMessage(message);
    f.setSuggestion(suggestion);
    f.setConfidence(new BigDecimal(confidence).setScale(2, RoundingMode.HALF_UP));
    return f;
  }

  private ValidationFindingEntity baseFinding(
      TenantScope scope,
      SubmissionCampaignEntity campaign,
      String entityType,
      String entityId,
      String label) {
    ValidationFindingEntity f = new ValidationFindingEntity();
    f.setOrganizationId(scope.organizationId());
    f.setCampaignId(campaign.getId());
    f.setEntityType(entityType);
    f.setEntityId(entityId);
    f.setEntityLabel(label);
    f.setSeverity("WARN");
    f.setSource(SOURCE_AI);
    f.setStatus("OPEN");
    return f;
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private AiScanJobResponse toResponse(AiScanJobEntity j) {
    return new AiScanJobResponse(
        j.getId(),
        j.getCampaignId(),
        j.getStatus(),
        j.getProvider(),
        j.getWarnCount(),
        j.getRecordsScanned(),
        j.getResultMessage(),
        j.isFallbackUsed(),
        j.getCreatedBy(),
        j.getCreatedAt(),
        j.getStartedAt(),
        j.getCompletedAt());
  }

  private static TenantScope copyScope(TenantScope scope) {
    return new TenantScope(
        scope.organizationId(),
        scope.branchId(),
        scope.academicSessionId(),
        scope.userId(),
        scope.roleCode());
  }

  private static String label(String entityType, Map<String, Object> row) {
    if ("STAFF".equals(entityType)) {
      return first(str(row.get("fullName")), str(row.get("employeeNo")), str(row.get("id")));
    }
    return first(str(row.get("fullName")), str(row.get("admissionNo")), str(row.get("id")));
  }

  private static String normalizeName(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  private static boolean blank(Object v) {
    return v == null || String.valueOf(v).trim().isEmpty();
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static String first(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return "";
  }

  private static int levenshtein(String a, String b) {
    int[] prev = new int[b.length() + 1];
    int[] cur = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      cur[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] tmp = prev;
      prev = cur;
      cur = tmp;
    }
    return prev[b.length()];
  }
}
