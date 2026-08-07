package com.sugamflow.school.compliance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.dto.ReadinessResponse;
import com.sugamflow.school.compliance.dto.ValidateRunRequest;
import com.sugamflow.school.compliance.dto.ValidateRunResponse;
import com.sugamflow.school.compliance.dto.ValidationFindingResponse;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.integration.MasterDataClient;
import com.sugamflow.school.compliance.persistence.entity.ComplianceFieldMapEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;
import com.sugamflow.school.compliance.persistence.entity.ValidationFindingEntity;
import com.sugamflow.school.compliance.persistence.entity.ValidationRuleEntity;
import com.sugamflow.school.compliance.persistence.repo.ComplianceFieldMapRepository;
import com.sugamflow.school.compliance.persistence.repo.SubmissionCampaignRepository;
import com.sugamflow.school.compliance.persistence.repo.ValidationFindingRepository;
import com.sugamflow.school.compliance.persistence.repo.ValidationRuleRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class ValidationEngineService {
  public static final String FEATURE_CBSE_COMPLIANCE = ComplianceService.FEATURE_CBSE_COMPLIANCE;

  private final ConfigEngineClient configEngineClient;
  private final MasterDataClient masterDataClient;
  private final ComplianceFieldMapRepository fieldMapRepository;
  private final ValidationRuleRepository ruleRepository;
  private final ValidationFindingRepository findingRepository;
  private final SubmissionCampaignRepository campaignRepository;
  private final BoardPackService boardPackService;

  public ValidationEngineService(
      ConfigEngineClient configEngineClient,
      MasterDataClient masterDataClient,
      ComplianceFieldMapRepository fieldMapRepository,
      ValidationRuleRepository ruleRepository,
      ValidationFindingRepository findingRepository,
      SubmissionCampaignRepository campaignRepository,
      BoardPackService boardPackService) {
    this.configEngineClient = configEngineClient;
    this.masterDataClient = masterDataClient;
    this.fieldMapRepository = fieldMapRepository;
    this.ruleRepository = ruleRepository;
    this.findingRepository = findingRepository;
    this.campaignRepository = campaignRepository;
    this.boardPackService = boardPackService;
  }

  @Transactional
  public ValidateRunResponse validateLatestOrCreate(ValidateRunRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    BoardPackService.PackSelection pack =
        request != null
                && ((request.boardCode() != null && !request.boardCode().isBlank())
                    || (request.packKey() != null && !request.packKey().isBlank()))
            ? boardPackService.resolveSelection(request.boardCode(), request.packKey())
            : boardPackService.resolveForOrganization(scope.organizationId());
    String board = pack.boardCode();
    SubmissionCampaignEntity campaign =
        campaignRepository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId()).stream()
            .filter(
                c ->
                    board.equalsIgnoreCase(c.getBoardCode())
                        && !"SUBMITTED".equalsIgnoreCase(c.getStatus())
                        && !"ARCHIVED".equalsIgnoreCase(c.getStatus()))
            .findFirst()
            .orElseGet(() -> createCampaign(scope, pack, request));
    return runValidation(scope, campaign);
  }

  @Transactional
  public ValidateRunResponse validateCampaign(Long campaignId) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    SubmissionCampaignEntity campaign =
        campaignRepository
            .findById(campaignId)
            .filter(c -> scope.organizationId().equals(c.getOrganizationId()))
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "Campaign not found", HttpStatus.NOT_FOUND));
    return runValidation(scope, campaign);
  }

  @Transactional(readOnly = true)
  public PageResult<ValidationFindingResponse> findings(
      String entityType, String severity, Long campaignId, Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null || size < 1 ? 50 : Math.min(size, 200);
    PageRequest pr = PageRequest.of(p, s);
    Page<ValidationFindingEntity> result;
    if (campaignId != null) {
      result = findingRepository.findByCampaignIdAndStatus(campaignId, "OPEN", pr);
    } else if (notBlank(entityType) && notBlank(severity)) {
      result =
          findingRepository.findByOrganizationIdAndStatusAndEntityTypeAndSeverity(
              scope.organizationId(), "OPEN", entityType.trim().toUpperCase(Locale.ROOT),
              severity.trim().toUpperCase(Locale.ROOT), pr);
    } else if (notBlank(entityType)) {
      result =
          findingRepository.findByOrganizationIdAndStatusAndEntityType(
              scope.organizationId(), "OPEN", entityType.trim().toUpperCase(Locale.ROOT), pr);
    } else if (notBlank(severity)) {
      result =
          findingRepository.findByOrganizationIdAndStatusAndSeverity(
              scope.organizationId(), "OPEN", severity.trim().toUpperCase(Locale.ROOT), pr);
    } else {
      result =
          findingRepository.findByOrganizationIdAndStatus(scope.organizationId(), "OPEN", pr);
    }
    List<ValidationFindingResponse> items =
        result.getContent().stream().map(this::toFinding).toList();
    return PageResult.of(items, p, s, result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public ReadinessResponse readiness() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    long blockers =
        findingRepository.countByOrganizationIdAndStatusAndSeverity(
            scope.organizationId(), "OPEN", "BLOCKER");
    long warns =
        findingRepository.countByOrganizationIdAndStatusAndSeverity(
            scope.organizationId(), "OPEN", "WARN");
    long studentFindings =
        findingRepository.countByOrganizationIdAndStatusAndEntityType(
            scope.organizationId(), "OPEN", "STUDENT");
    long staffFindings =
        findingRepository.countByOrganizationIdAndStatusAndEntityType(
            scope.organizationId(), "OPEN", "STAFF");

    Map<String, String> labels = new HashMap<>();
    BoardPackService.PackSelection pack =
        boardPackService.resolveForOrganization(scope.organizationId());
    for (ComplianceFieldMapEntity map :
        fieldMapRepository.findByBoardCodeAndActiveTrueOrderBySortOrderAsc(pack.boardCode())) {
      labels.put(map.getEntityType() + "|" + map.getFieldKey(), map.getLabel());
    }

    List<ReadinessResponse.GapRow> gaps = new ArrayList<>();
    for (Object[] row : findingRepository.readinessGroups(scope.organizationId())) {
      String entityType = String.valueOf(row[0]);
      String fieldKey = row[1] == null ? "" : String.valueOf(row[1]);
      String severity = String.valueOf(row[2]);
      long count = row[3] instanceof Number n ? n.longValue() : 0L;
      String label =
          labels.getOrDefault(
              entityType + "|" + fieldKey, fieldKey.isEmpty() ? entityType : fieldKey);
      gaps.add(new ReadinessResponse.GapRow(entityType, fieldKey, severity, count, label));
    }
    gaps.sort(
        (a, b) -> {
          int sev = severityRank(b.severity()) - severityRank(a.severity());
          if (sev != 0) {
            return sev;
          }
          return Long.compare(b.count(), a.count());
        });

    SubmissionCampaignEntity latest =
        campaignRepository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId()).stream()
            .findFirst()
            .orElse(null);

    int readiness =
        blockers == 0 && warns == 0
            ? (latest == null ? 0 : 100)
            : (int)
                Math.max(
                    0,
                    Math.min(
                        99,
                        100
                            - blockers * 4
                            - Math.min(warns, 20)));

    return new ReadinessResponse(
        (int) Math.min(Integer.MAX_VALUE, studentFindings),
        (int) Math.min(Integer.MAX_VALUE, staffFindings),
        (int) blockers,
        (int) warns,
        readiness,
        gaps,
        latest != null ? latest.getId() : null,
        latest != null ? latest.getStatus() : null);
  }

  private ValidateRunResponse runValidation(TenantScope scope, SubmissionCampaignEntity campaign) {
    if (CampaignWorkflowService.isImmutableStatus(campaign.getStatus())) {
      throw new ComplianceException(
          "LOCKED",
          "Campaign is " + campaign.getStatus() + " — unlock/archive cycle required before re-validate",
          HttpStatus.CONFLICT);
    }
    findingRepository.deleteByCampaignId(campaign.getId());
    campaign.setStatus("VALIDATING");
    campaignRepository.save(campaign);

    String board = campaign.getBoardCode() != null ? campaign.getBoardCode() : "CBSE";
    List<ComplianceFieldMapEntity> maps =
        fieldMapRepository.findByBoardCodeAndActiveTrueOrderBySortOrderAsc(board);
    List<ValidationRuleEntity> rules = ruleRepository.findByBoardCodeAndActiveTrue(board);

    List<Map<String, Object>> students = masterDataClient.listStudentProjections(scope);
    List<Map<String, Object>> staff = masterDataClient.listStaffProjections(scope);

    List<ValidationFindingEntity> findings = new ArrayList<>();
    findings.addAll(validateEntities(scope, campaign, "STUDENT", students, maps, rules));
    findings.addAll(validateEntities(scope, campaign, "STAFF", staff, maps, rules));

    if (!findings.isEmpty()) {
      findingRepository.saveAll(findings);
    }

    int blockers = (int) findings.stream().filter(f -> "BLOCKER".equalsIgnoreCase(f.getSeverity())).count();
    int warns = (int) findings.stream().filter(f -> "WARN".equalsIgnoreCase(f.getSeverity())).count();
    int checked = students.size() + staff.size();
    BigDecimal score = score(checked, blockers, warns);

    campaign.setBlockerCount(blockers);
    campaign.setWarnCount(warns);
    campaign.setComplianceScore(score);
    campaign.setStatus(blockers == 0 ? "READY" : "NEEDS_FIX");
    campaignRepository.save(campaign);

    return new ValidateRunResponse(
        campaign.getId(),
        campaign.getTitle(),
        campaign.getStatus(),
        score,
        checked,
        blockers,
        warns,
        Instant.now());
  }

  private List<ValidationFindingEntity> validateEntities(
      TenantScope scope,
      SubmissionCampaignEntity campaign,
      String entityType,
      List<Map<String, Object>> rows,
      List<ComplianceFieldMapEntity> maps,
      List<ValidationRuleEntity> rules) {
    List<ComplianceFieldMapEntity> entityMaps =
        maps.stream().filter(m -> entityType.equalsIgnoreCase(m.getEntityType())).toList();
    List<ValidationRuleEntity> entityRules =
        rules.stream().filter(r -> entityType.equalsIgnoreCase(r.getEntityType())).toList();

    List<ValidationFindingEntity> out = new ArrayList<>();
    Map<String, List<String>> uniqueIndex = new HashMap<>();

    for (Map<String, Object> row : rows) {
      String entityId = str(row.get("id"));
      String label = entityLabel(entityType, row);
      for (ComplianceFieldMapEntity map : entityMaps) {
        String value = resolve(row, map.getSourcePath());
        if (map.isRequired() && value.isEmpty()) {
          out.add(
              finding(
                  scope,
                  campaign,
                  entityType,
                  entityId,
                  label,
                  map.getFieldKey(),
                  "REQUIRED_" + map.getFieldKey().toUpperCase(Locale.ROOT),
                  map.getSeverity(),
                  map.getLabel() + " is required for " + label + ".",
                  "Open the " + entityType.toLowerCase(Locale.ROOT) + " record and fill "
                      + map.getLabel() + "."));
        } else if (!value.isEmpty()
            && map.getFormatRegex() != null
            && !map.getFormatRegex().isBlank()) {
          try {
            if (!Pattern.compile(map.getFormatRegex()).matcher(value).matches()) {
              out.add(
                  finding(
                      scope,
                      campaign,
                      entityType,
                      entityId,
                      label,
                      map.getFieldKey(),
                      "FORMAT_" + map.getFieldKey().toUpperCase(Locale.ROOT),
                      map.getSeverity(),
                      map.getLabel() + " has an invalid format for " + label + ".",
                      "Correct " + map.getLabel() + " to match the expected pattern."));
            }
          } catch (Exception ignored) {
            // bad regex in seed — skip
          }
        }
      }

      for (ValidationRuleEntity rule : entityRules) {
        if ("UNIQUE".equalsIgnoreCase(rule.getRuleType())) {
          String field = str(rule.getConfigJson().get("field"));
          if (field.isEmpty()) {
            continue;
          }
          String value = resolve(row, field);
          boolean skipBlank =
              Boolean.TRUE.equals(rule.getConfigJson().get("skipBlank")) || value.isEmpty();
          if (skipBlank && value.isEmpty()) {
            continue;
          }
          String key = field + "|" + value.toLowerCase(Locale.ROOT);
          uniqueIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(entityId + "|" + label);
        } else if ("CROSS_FIELD".equalsIgnoreCase(rule.getRuleType())
            && "STUDENT_AGE_CLASS".equalsIgnoreCase(rule.getRuleCode())) {
          if (ageClassMismatch(row)) {
            out.add(
                finding(
                    scope,
                    campaign,
                    entityType,
                    entityId,
                    label,
                    "dateOfBirth",
                    rule.getRuleCode(),
                    rule.getSeverity(),
                    template(
                        rule.getMessageTemplate(),
                        Map.of("label", label, "value", resolve(row, "classSection"))),
                    "Verify date of birth and class/section alignment."));
          }
        }
      }
    }

    for (ValidationRuleEntity rule : entityRules) {
      if (!"UNIQUE".equalsIgnoreCase(rule.getRuleType())) {
        continue;
      }
      String field = str(rule.getConfigJson().get("field"));
      for (Map.Entry<String, List<String>> e : uniqueIndex.entrySet()) {
        if (!e.getKey().startsWith(field + "|") || e.getValue().size() < 2) {
          continue;
        }
        String value = e.getKey().substring(field.length() + 1);
        for (String pair : e.getValue()) {
          String[] parts = pair.split("\\|", 2);
          String entityId = parts[0];
          String label = parts.length > 1 ? parts[1] : entityId;
          out.add(
              finding(
                  scope,
                  campaign,
                  entityType,
                  entityId,
                  label,
                  field,
                  rule.getRuleCode(),
                  rule.getSeverity(),
                  template(
                      rule.getMessageTemplate(),
                      Map.of("value", value, "label", label)),
                  "Resolve duplicate " + field + " across master records."));
        }
      }
    }
    return out;
  }

  private static boolean ageClassMismatch(Map<String, Object> row) {
    String dob = resolve(row, "dateOfBirth");
    String cls = resolve(row, "classSection");
    if (dob.isEmpty() || cls.isEmpty()) {
      return false;
    }
    LocalDate birth;
    try {
      birth = LocalDate.parse(dob.length() >= 10 ? dob.substring(0, 10) : dob);
    } catch (DateTimeParseException ex) {
      return false;
    }
    int age = Period.between(birth, LocalDate.now()).getYears();
    String lower = cls.toLowerCase(Locale.ROOT);
    if (lower.contains("nur") || lower.contains("kg") || lower.contains("pre")) {
      return age < 2 || age > 7;
    }
    Integer grade = extractGrade(cls);
    if (grade == null) {
      return false;
    }
    int expectedMin = grade + 4;
    int expectedMax = grade + 8;
    return age < expectedMin || age > expectedMax;
  }

  private static Integer extractGrade(String classSection) {
    String s = classSection.trim().toUpperCase(Locale.ROOT);
    if (s.startsWith("XII") || s.matches(".*\\b12\\b.*")) {
      return 12;
    }
    if (s.startsWith("XI") || s.matches(".*\\b11\\b.*")) {
      return 11;
    }
    if (s.startsWith("VIII") || s.matches(".*\\b8\\b.*")) {
      return 8;
    }
    if (s.startsWith("VII") || s.matches(".*\\b7\\b.*")) {
      return 7;
    }
    if (s.startsWith("VI") || s.matches(".*\\b6\\b.*")) {
      return 6;
    }
    if (s.startsWith("IV") || s.matches(".*\\b4\\b.*")) {
      return 4;
    }
    if (s.startsWith("III") || s.matches(".*\\b3\\b.*")) {
      return 3;
    }
    if (s.startsWith("II") || s.matches(".*\\b2\\b.*")) {
      return 2;
    }
    if (s.startsWith("IX") || s.matches(".*\\b9\\b.*")) {
      return 9;
    }
    if (s.startsWith("X") || s.matches(".*\\b10\\b.*")) {
      return 10;
    }
    if (s.startsWith("V") || s.matches(".*\\b5\\b.*")) {
      return 5;
    }
    if (s.startsWith("I") || s.matches(".*\\b1\\b.*")) {
      return 1;
    }
    return null;
  }

  private SubmissionCampaignEntity createCampaign(
      TenantScope scope, BoardPackService.PackSelection pack, ValidateRunRequest request) {
    SubmissionCampaignEntity c = new SubmissionCampaignEntity();
    c.setOrganizationId(scope.organizationId());
    c.setBoardCode(pack.boardCode());
    c.setPackKey(pack.packKey());
    c.setTitle(
        request != null && request.title() != null && !request.title().isBlank()
            ? request.title().trim()
            : pack.boardCode() + " data validation");
    if (request != null && request.academicSessionId() != null) {
      c.setAcademicSessionId(request.academicSessionId().trim());
    }
    c.setStatus("DRAFT");
    return campaignRepository.save(c);
  }

  private ValidationFindingEntity finding(
      TenantScope scope,
      SubmissionCampaignEntity campaign,
      String entityType,
      String entityId,
      String entityLabel,
      String fieldKey,
      String ruleCode,
      String severity,
      String message,
      String suggestion) {
    ValidationFindingEntity f = new ValidationFindingEntity();
    f.setOrganizationId(scope.organizationId());
    f.setCampaignId(campaign.getId());
    f.setEntityType(entityType);
    f.setEntityId(entityId);
    f.setEntityLabel(entityLabel);
    f.setFieldKey(fieldKey);
    f.setRuleCode(ruleCode);
    f.setSeverity(severity == null ? "WARN" : severity.toUpperCase(Locale.ROOT));
    f.setMessage(message);
    f.setSuggestion(suggestion);
    f.setStatus("OPEN");
    f.setSource("RULE");
    return f;
  }

  private ValidationFindingResponse toFinding(ValidationFindingEntity f) {
    return new ValidationFindingResponse(
        f.getId(),
        f.getCampaignId(),
        f.getEntityType(),
        f.getEntityId(),
        f.getEntityLabel(),
        f.getFieldKey(),
        f.getRuleCode(),
        f.getSeverity(),
        f.getMessage(),
        f.getSuggestion(),
        f.getStatus(),
        f.getSource() == null ? "RULE" : f.getSource(),
        f.getConfidence(),
        f.getCreatedAt());
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private static BigDecimal score(int checked, int blockers, int warns) {
    if (checked <= 0 && blockers == 0 && warns == 0) {
      return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
    }
    double penalty = blockers * 4.0 + Math.min(warns, 40) * 0.5;
    double value = Math.max(0, 100.0 - penalty);
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }

  private static String entityLabel(String entityType, Map<String, Object> row) {
    if ("STAFF".equalsIgnoreCase(entityType)) {
      return firstNonBlank(str(row.get("fullName")), str(row.get("employeeNo")), str(row.get("id")));
    }
    return firstNonBlank(
        str(row.get("fullName")), str(row.get("admissionNo")), str(row.get("id")));
  }

  private static String resolve(Map<String, Object> row, String path) {
    if (path == null || path.isBlank() || row == null) {
      return "";
    }
    if (!path.contains(".")) {
      return str(row.get(path));
    }
    Object cur = row;
    for (String part : path.split("\\.")) {
      if (!(cur instanceof Map<?, ?> map)) {
        return "";
      }
      cur = map.get(part);
    }
    return str(cur);
  }

  private static String template(String tmpl, Map<String, String> vars) {
    String out = tmpl == null ? "" : tmpl;
    for (Map.Entry<String, String> e : vars.entrySet()) {
      out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
    }
    return out;
  }

  private static int severityRank(String severity) {
    if ("BLOCKER".equalsIgnoreCase(severity)) {
      return 2;
    }
    if ("WARN".equalsIgnoreCase(severity)) {
      return 1;
    }
    return 0;
  }

  private static boolean notBlank(String v) {
    return v != null && !v.isBlank();
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return "";
  }
}
