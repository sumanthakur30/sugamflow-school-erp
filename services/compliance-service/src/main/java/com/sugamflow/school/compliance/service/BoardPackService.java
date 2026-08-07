package com.sugamflow.school.compliance.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.dto.BoardDefinitionResponse;
import com.sugamflow.school.compliance.dto.ComplianceTemplateRequest;
import com.sugamflow.school.compliance.dto.ComplianceTemplateResponse;
import com.sugamflow.school.compliance.dto.ComplianceTemplateUpdateRequest;
import com.sugamflow.school.compliance.dto.FieldMapResponse;
import com.sugamflow.school.compliance.dto.FieldMapUpdateRequest;
import com.sugamflow.school.compliance.dto.ValidationRuleResponse;
import com.sugamflow.school.compliance.dto.ValidationRuleUpdateRequest;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.persistence.entity.BoardDefinitionEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceFieldMapEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceTemplateEntity;
import com.sugamflow.school.compliance.persistence.entity.SchoolComplianceProfileEntity;
import com.sugamflow.school.compliance.persistence.entity.ValidationRuleEntity;
import com.sugamflow.school.compliance.persistence.repo.BoardDefinitionRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceFieldMapRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceTemplateRepository;
import com.sugamflow.school.compliance.persistence.repo.SchoolComplianceProfileRepository;
import com.sugamflow.school.compliance.persistence.repo.ValidationRuleRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class BoardPackService {
  private static final Set<String> PLATFORM_ROLES = Set.of("SHOP_OWNER", "SUPER_ADMIN", "ADMIN");

  private final BoardDefinitionRepository boardRepository;
  private final ComplianceTemplateRepository templateRepository;
  private final ComplianceFieldMapRepository fieldMapRepository;
  private final ValidationRuleRepository ruleRepository;
  private final SchoolComplianceProfileRepository profileRepository;
  private final ConfigEngineClient configEngineClient;

  public BoardPackService(
      BoardDefinitionRepository boardRepository,
      ComplianceTemplateRepository templateRepository,
      ComplianceFieldMapRepository fieldMapRepository,
      ValidationRuleRepository ruleRepository,
      SchoolComplianceProfileRepository profileRepository,
      ConfigEngineClient configEngineClient) {
    this.boardRepository = boardRepository;
    this.templateRepository = templateRepository;
    this.fieldMapRepository = fieldMapRepository;
    this.ruleRepository = ruleRepository;
    this.profileRepository = profileRepository;
    this.configEngineClient = configEngineClient;
  }

  public record PackSelection(String boardCode, String packKey) {}

  @Transactional(readOnly = true)
  public PackSelection resolveForOrganization(String organizationId) {
    SchoolComplianceProfileEntity profile =
        profileRepository.findByOrganizationId(organizationId).orElse(null);
    String board =
        profile != null && profile.getBoardCode() != null && !profile.getBoardCode().isBlank()
            ? profile.getBoardCode().trim().toUpperCase(Locale.ROOT)
            : "CBSE";
    String pack =
        profile != null && profile.getActivePackKey() != null && !profile.getActivePackKey().isBlank()
            ? profile.getActivePackKey().trim()
            : defaultPackKey(board);
    return new PackSelection(board, pack);
  }

  @Transactional(readOnly = true)
  public PackSelection resolveSelection(String boardCode, String packKey) {
    if (packKey != null && !packKey.isBlank()) {
      ComplianceTemplateEntity tmpl =
          templateRepository
              .findByPackKey(packKey.trim())
              .orElseThrow(
                  () ->
                      new ComplianceException(
                          "NOT_FOUND", "Unknown pack: " + packKey, HttpStatus.BAD_REQUEST));
      return new PackSelection(tmpl.getBoardCode().toUpperCase(Locale.ROOT), tmpl.getPackKey());
    }
    String board =
        boardCode == null || boardCode.isBlank()
            ? "CBSE"
            : boardCode.trim().toUpperCase(Locale.ROOT);
    return new PackSelection(board, defaultPackKey(board));
  }

  public String defaultPackKey(String boardCode) {
    String board =
        boardCode == null || boardCode.isBlank()
            ? "CBSE"
            : boardCode.trim().toUpperCase(Locale.ROOT);
    return templateRepository
        .findByBoardCodeAndActiveTrueAndStatusOrderByPackKeyAsc(board, "PUBLISHED")
        .stream()
        .findFirst()
        .map(ComplianceTemplateEntity::getPackKey)
        .orElse(board + "-2026.1");
  }

  @Transactional(readOnly = true)
  public List<BoardDefinitionResponse> listBoards() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return boardRepository.findByActiveTrueOrderByCodeAsc().stream()
        .map(b -> new BoardDefinitionResponse(b.getCode(), b.getName(), b.isActive()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ComplianceTemplateResponse> listPublishedPacks() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return templateRepository
        .findByActiveTrueAndStatusOrderByBoardCodeAscPackKeyAsc("PUBLISHED")
        .stream()
        .map(ComplianceTemplateResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<ComplianceTemplateResponse> listAllTemplates() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requirePlatformAdmin(scope);
    return templateRepository.findAllByOrderByBoardCodeAscPackKeyAsc().stream()
        .map(ComplianceTemplateResponse::from)
        .toList();
  }

  @Transactional
  public ComplianceTemplateResponse createTemplate(ComplianceTemplateRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requirePlatformAdmin(scope);
    String packKey = request.packKey().trim();
    if (templateRepository.findByPackKey(packKey).isPresent()) {
      throw new ComplianceException(
          "CONFLICT", "Pack key already exists: " + packKey, HttpStatus.CONFLICT);
    }
    String board = request.boardCode().trim().toUpperCase(Locale.ROOT);
    BoardDefinitionEntity boardDef =
        boardRepository
            .findById(board)
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "Unknown board: " + board, HttpStatus.BAD_REQUEST));
    if (!boardDef.isActive()) {
      throw new ComplianceException(
          "INVALID", "Board is inactive: " + board, HttpStatus.BAD_REQUEST);
    }
    ComplianceTemplateEntity e = new ComplianceTemplateEntity();
    e.setPackKey(packKey);
    e.setBoardCode(board);
    e.setVersionLabel(request.versionLabel().trim());
    e.setTitle(request.title().trim());
    e.setDescription(trimToNull(request.description()));
    e.setStatus(
        request.status() == null || request.status().isBlank()
            ? "PUBLISHED"
            : request.status().trim().toUpperCase(Locale.ROOT));
    e.setActive(request.active() == null || request.active());
    if ("PUBLISHED".equalsIgnoreCase(e.getStatus())) {
      e.setPublishedAt(Instant.now());
    }
    return ComplianceTemplateResponse.from(templateRepository.save(e));
  }

  @Transactional
  public ComplianceTemplateResponse updateTemplate(Long id, ComplianceTemplateUpdateRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requirePlatformAdmin(scope);
    ComplianceTemplateEntity e =
        templateRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "Template not found", HttpStatus.NOT_FOUND));
    if (request.title() != null && !request.title().isBlank()) {
      e.setTitle(request.title().trim());
    }
    if (request.description() != null) {
      e.setDescription(trimToNull(request.description()));
    }
    if (request.status() != null && !request.status().isBlank()) {
      String status = request.status().trim().toUpperCase(Locale.ROOT);
      e.setStatus(status);
      if ("PUBLISHED".equals(status) && e.getPublishedAt() == null) {
        e.setPublishedAt(Instant.now());
      }
    }
    if (request.active() != null) {
      e.setActive(request.active());
    }
    return ComplianceTemplateResponse.from(templateRepository.save(e));
  }

  @Transactional(readOnly = true)
  public List<FieldMapResponse> listFieldMaps(String boardCode) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requirePlatformAdmin(scope);
    String board = boardCode.trim().toUpperCase(Locale.ROOT);
    return fieldMapRepository.findByBoardCodeOrderBySortOrderAscEntityTypeAsc(board).stream()
        .map(FieldMapResponse::from)
        .toList();
  }

  @Transactional
  public FieldMapResponse updateFieldMap(Long id, FieldMapUpdateRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requirePlatformAdmin(scope);
    ComplianceFieldMapEntity e =
        fieldMapRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "Field map not found", HttpStatus.NOT_FOUND));
    if (request.label() != null && !request.label().isBlank()) {
      e.setLabel(request.label().trim());
    }
    if (request.required() != null) {
      e.setRequired(request.required());
    }
    if (request.severity() != null && !request.severity().isBlank()) {
      e.setSeverity(request.severity().trim().toUpperCase(Locale.ROOT));
    }
    if (request.formatRegex() != null) {
      e.setFormatRegex(trimToNull(request.formatRegex()));
    }
    if (request.sortOrder() != null) {
      e.setSortOrder(request.sortOrder());
    }
    if (request.active() != null) {
      e.setActive(request.active());
    }
    return FieldMapResponse.from(fieldMapRepository.save(e));
  }

  @Transactional(readOnly = true)
  public List<ValidationRuleResponse> listRules(String boardCode) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requirePlatformAdmin(scope);
    String board = boardCode.trim().toUpperCase(Locale.ROOT);
    return ruleRepository.findByBoardCodeOrderByRuleCodeAsc(board).stream()
        .map(ValidationRuleResponse::from)
        .toList();
  }

  @Transactional
  public ValidationRuleResponse updateRule(Long id, ValidationRuleUpdateRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requirePlatformAdmin(scope);
    ValidationRuleEntity e =
        ruleRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ComplianceException(
                        "NOT_FOUND", "Validation rule not found", HttpStatus.NOT_FOUND));
    if (request.severity() != null && !request.severity().isBlank()) {
      e.setSeverity(request.severity().trim().toUpperCase(Locale.ROOT));
    }
    if (request.messageTemplate() != null) {
      e.setMessageTemplate(trimToNull(request.messageTemplate()));
    }
    if (request.configJson() != null) {
      e.setConfigJson(request.configJson());
    }
    if (request.active() != null) {
      e.setActive(request.active());
    }
    return ValidationRuleResponse.from(ruleRepository.save(e));
  }

  /** Apply board + pack onto a profile, resolving defaults and keeping them consistent. */
  public void applyBoardPack(
      SchoolComplianceProfileEntity profile, String boardCode, String packKey) {
    if (packKey != null && !packKey.isBlank()) {
      PackSelection sel = resolveSelection(null, packKey);
      profile.setBoardCode(sel.boardCode());
      profile.setActivePackKey(sel.packKey());
      return;
    }
    if (boardCode != null && !boardCode.isBlank()) {
      String board = boardCode.trim().toUpperCase(Locale.ROOT);
      profile.setBoardCode(board);
      String current = profile.getActivePackKey();
      if (current == null
          || current.isBlank()
          || !current.toUpperCase(Locale.ROOT).startsWith(board + "-")) {
        profile.setActivePackKey(defaultPackKey(board));
      }
      return;
    }
    if (profile.getActivePackKey() == null || profile.getActivePackKey().isBlank()) {
      profile.setActivePackKey(defaultPackKey(profile.getBoardCode()));
    }
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private static void requirePlatformAdmin(TenantScope scope) {
    String role = PersonaRoles.normalize(scope.roleCode());
    if (!PLATFORM_ROLES.contains(role)) {
      throw new ComplianceException(
          "FORBIDDEN",
          "Platform template management requires SHOP_OWNER / SUPER_ADMIN / ADMIN.",
          HttpStatus.FORBIDDEN);
    }
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
