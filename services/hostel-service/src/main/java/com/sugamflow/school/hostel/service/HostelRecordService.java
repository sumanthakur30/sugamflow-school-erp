package com.sugamflow.school.hostel.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.hostel.config.HostelProperties;
import com.sugamflow.school.hostel.integration.ConfigEngineClient;
import com.sugamflow.school.hostel.integration.NotificationDeliveryClient;
import com.sugamflow.school.hostel.persistence.entity.HostelRecordEntity;
import com.sugamflow.school.hostel.persistence.repo.HostelRecordRepository;
import com.sugamflow.school.hostel.web.HostelException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HostelRecordService {

  public static final String FEATURE_HOSTEL = "FEATURE_HOSTEL";
  public static final String MODULE_HOSTEL = "hostel";
  public static final String ACTION_BLOCK = "BLOCK_HOSTEL";
  public static final String ACTION_NOTIFY = "NOTIFY_HOSTEL";

  private final HostelRecordRepository repository;
  private final ConfigEngineClient engines;
  private final NotificationDeliveryClient notificationDelivery;
  private final HostelProperties properties;
  private final HostelOpsService opsService;

  public HostelRecordService(
      HostelRecordRepository repository,
      ConfigEngineClient engines,
      NotificationDeliveryClient notificationDelivery,
      HostelProperties properties,
      HostelOpsService opsService) {
    this.repository = repository;
    this.engines = engines;
    this.notificationDelivery = notificationDelivery;
    this.properties = properties;
    this.opsService = opsService;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_HOSTEL);
    String formKey = resolveFormKey(module);
    String workflowKey = resolveWorkflowKey(module);
    Map<String, Object> form = engines.getForm(scope, formKey);
    Map<String, Object> workflow = engines.getWorkflow(scope, workflowKey);
    if (form == null) {
      throw new HostelException("FORM_MISSING", "Form definition not found: " + formKey);
    }
    if (workflow == null) {
      throw new HostelException("WORKFLOW_MISSING", "Workflow definition not found: " + workflowKey);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("module", module);
    out.put("formKey", formKey);
    out.put("workflowKey", workflowKey);
    out.put("form", form);
    out.put("workflow", workflow);
    if (engines.isFeatureEnabled(scope, com.sugamflow.school.hostel.ops.HostelOpsCatalog.FEATURE_OPS_DEPTH)) {
      try {
        out.put("ops", opsService.bootstrap());
      } catch (HostelException ex) {
        out.put("ops", Map.of("featureEnabled", false, "error", ex.getMessage()));
      }
    }
    return out;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    PageQuery q = PageQuery.of(page, size); Pageable pageable = PageRequest.of(q.page(), q.size());
    Page<HostelRecordEntity> result;
    if (scope.branchId() != null && !scope.branchId().isBlank() && scope.academicSessionId() != null && !scope.academicSessionId().isBlank()) {
      result = repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(scope.organizationId(), scope.branchId(), scope.academicSessionId(), pageable);
    } else { result = repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId(), pageable); }
    return PageResult.of(result.map(this::toDto).getContent(), q.page(), q.size(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return toDto(requireRecord(id, scope.organizationId()));
  }

  @Transactional
  public Map<String, Object> submit(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requireModuleEnabled(scope);

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_HOSTEL);
    String formKey = stringOr(body.get("formKey"), resolveFormKey(module));
    String workflowKey = stringOr(body.get("workflowKey"), resolveWorkflowKey(module));

    @SuppressWarnings("unchecked")
    Map<String, Object> answers =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();

    Map<String, Object> form = engines.getForm(scope, formKey);
    if (form == null) {
      throw new HostelException("FORM_MISSING", "Form definition not found: " + formKey);
    }
    validateMandatory(form, answers);

    Map<String, Object> workflow = engines.getWorkflow(scope, workflowKey);
    if (workflow == null) {
      throw new HostelException("WORKFLOW_MISSING", "Workflow definition not found: " + workflowKey);
    }

    Map<String, Object> ruleContext = new LinkedHashMap<>();
    ruleContext.put("hostel", answers);
    List<String> matched = engines.evaluateRules(scope, ruleContext);
    if (matched.contains(ACTION_BLOCK)) {
      throw new HostelException(
          ACTION_BLOCK, "Hostel allocation blocked by rule engine (BLOCK_HOSTEL).");
    }

    WorkflowStep first = firstStep(workflow);
    HostelRecordEntity entity = new HostelRecordEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setFormKey(formKey);
    entity.setWorkflowKey(workflowKey);
    entity.setStatus("IN_PROGRESS");
    entity.setCurrentStepSequence(first.sequence());
    entity.setCurrentStepName(first.name());
    entity.setAssigneeRole(first.assignRole());
    entity.setAnswers(answers);
    entity.setMatchedActions(matched);
    entity.setCreatedBy(scope.userId());
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    List<Map<String, Object>> history = new ArrayList<>();
    history.add(
        event(
            "SUBMITTED",
            scope.userId(),
            scope.roleCode(),
            "Hostel record submitted",
            first.sequence(),
            first.name()));
    entity.setHistory(history);

    List<Map<String, Object>> intents = new ArrayList<>();
    intents.add(recordNotification(scope, entity, "HOSTEL_SUBMITTED"));
    if (matched.contains(ACTION_NOTIFY)) {
      intents.add(recordNotification(scope, entity, "HOSTEL_RULE_NOTIFY"));
    }
    entity.setNotificationIntents(intents);
    return toDto(repository.save(entity));
  }

  @Transactional
  public Map<String, Object> act(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    String action = String.valueOf(body.getOrDefault("action", "")).trim().toUpperCase();
    String comment = body.get("comment") != null ? String.valueOf(body.get("comment")) : null;
    HostelRecordEntity entity = requireRecord(id, scope.organizationId());
    if ("REJECTED".equals(entity.getStatus()) || "APPROVED".equals(entity.getStatus())) {
      throw new HostelException("TERMINAL", "Record is already " + entity.getStatus());
    }
    Map<String, Object> workflow = engines.getWorkflow(scope, entity.getWorkflowKey());
    if (workflow == null) {
      throw new HostelException(
          "WORKFLOW_MISSING", "Workflow definition not found: " + entity.getWorkflowKey());
    }
    return switch (action) {
      case "REJECT" -> reject(entity, scope, comment);
      case "REQUEST_INFO" -> requestInfo(entity, scope, comment);
      case "APPROVE" -> approve(entity, scope, workflow, comment);
      default -> throw new HostelException(
          "UNKNOWN_ACTION", "Supported actions: APPROVE, REJECT, REQUEST_INFO");
    };
  }

  private Map<String, Object> reject(
      HostelRecordEntity entity, TenantScope scope, String comment) {
    entity.setStatus("REJECTED");
    entity.setUpdatedAt(Instant.now());
    entity.getHistory()
        .add(
            event(
                "REJECTED",
                scope.userId(),
                scope.roleCode(),
                comment != null ? comment : "Rejected",
                entity.getCurrentStepSequence(),
                entity.getCurrentStepName()));
    entity.getNotificationIntents().add(recordNotification(scope, entity, "HOSTEL_REJECTED"));
    return toDto(repository.save(entity));
  }

  private Map<String, Object> requestInfo(
      HostelRecordEntity entity, TenantScope scope, String comment) {
    entity.setStatus("INFO_REQUESTED");
    entity.setUpdatedAt(Instant.now());
    entity.getHistory()
        .add(
            event(
                "REQUEST_INFO",
                scope.userId(),
                scope.roleCode(),
                comment != null ? comment : "More information requested",
                entity.getCurrentStepSequence(),
                entity.getCurrentStepName()));
    entity.getNotificationIntents()
        .add(recordNotification(scope, entity, "HOSTEL_INFO_REQUESTED"));
    return toDto(repository.save(entity));
  }

  private Map<String, Object> approve(
      HostelRecordEntity entity,
      TenantScope scope,
      Map<String, Object> workflow,
      String comment) {
    List<WorkflowStep> steps = steps(workflow);
    WorkflowStep current =
        steps.stream()
            .filter(s -> s.sequence() == entity.getCurrentStepSequence())
            .findFirst()
            .orElseThrow(
                () ->
                    new HostelException(
                        "STEP_MISSING",
                        "Current workflow step not found: " + entity.getCurrentStepSequence()));
    entity.getHistory()
        .add(
            event(
                "STEP_APPROVED",
                scope.userId(),
                scope.roleCode(),
                comment != null ? comment : "Approved step " + current.name(),
                current.sequence(),
                current.name()));
    WorkflowStep next =
        steps.stream()
            .filter(s -> s.sequence() > current.sequence())
            .min(Comparator.comparingInt(WorkflowStep::sequence))
            .orElse(null);
    if (next == null
        || "SYSTEM".equalsIgnoreCase(next.assignRole())
        || "Completed".equalsIgnoreCase(next.name())) {
      entity.setStatus("APPROVED");
      if (next != null) {
        entity.setCurrentStepSequence(next.sequence());
        entity.setCurrentStepName(next.name());
        entity.setAssigneeRole(next.assignRole());
      }
      entity.getHistory()
          .add(
              event(
                  "APPROVED",
                  scope.userId(),
                  scope.roleCode(),
                  "Hostel record approved",
                  entity.getCurrentStepSequence(),
                  entity.getCurrentStepName()));
      onFinalApprove(entity, scope);
    } else {
      entity.setStatus("IN_PROGRESS");
      entity.setCurrentStepSequence(next.sequence());
      entity.setCurrentStepName(next.name());
      entity.setAssigneeRole(next.assignRole());
      entity.getNotificationIntents()
          .add(recordNotification(scope, entity, "HOSTEL_STEP_ADVANCED"));
    }
    entity.setUpdatedAt(Instant.now());
    return toDto(repository.save(entity));
  }

  private void onFinalApprove(HostelRecordEntity entity, TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_HOSTEL);
    Map<String, Object> settings = moduleSettingsMap(module);
    boolean notify =
        settings.get("notifyOnApprove") == null
            || Boolean.TRUE.equals(settings.get("notifyOnApprove"));
    Map<String, Object> intent =
        notify
            ? deliverApproveNotifications(scope, entity, settings)
            : recordNotification(scope, entity, "HOSTEL_APPROVED");
    entity.getNotificationIntents().add(intent);
  }

  private Map<String, Object> deliverApproveNotifications(
      TenantScope scope, HostelRecordEntity entity, Map<String, Object> settings) {
    String intent = "HOSTEL_APPROVED";
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("hostel", entity.getAnswers());
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", scope.organizationId());
    context.put("branchId", scope.branchId());
    context.put("academicSessionId", scope.academicSessionId());
    context.put("recordId", entity.getId().toString());
    variables.put("context", context);

    String templateId = stringOr(settings.get("approveNotificationTemplateId"), "hostel_approved");
    Map<String, Object> resolved =
        engines.resolveNotification(scope, "HOSTEL", templateId, variables);
    if (resolved.isEmpty() || !Boolean.TRUE.equals(resolved.get("resolved"))) {
      resolved = engines.resolveNotification(scope, "HOSTEL", intent, variables);
    }

    List<String> channels = resolveApproveChannels(settings, resolved);
    String subject = String.valueOf(resolved.getOrDefault("subject", "Hostel allocation approved"));
    String body =
        String.valueOf(
            resolved.getOrDefault(
                "body",
                "Hostel allocation approved for "
                    + entity.getAnswers().getOrDefault("studentName", "student")));

    List<Map<String, Object>> deliveries = new ArrayList<>();
    for (String channel : channels) {
      deliveries.add(deliverChannel(scope, entity, channel, subject, body));
    }

    Map<String, Object> stored = new LinkedHashMap<>();
    stored.put("intent", intent);
    stored.put("at", Instant.now().toString());
    stored.put("preview", Map.of("subject", subject, "body", body, "channels", channels));
    stored.put("delivery", deliveries);
    return stored;
  }

  private Map<String, Object> deliverChannel(
      TenantScope scope,
      HostelRecordEntity entity,
      String channel,
      String subject,
      String body) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("channel", channel);
    row.put("at", Instant.now().toString());
    String upper = channel.toUpperCase();
    if ("IN_APP".equals(upper)) {
      row.put("status", "SENT");
      row.put("notificationId", "in-app-" + UUID.randomUUID());
      row.put("recipient", scope.userId());
      return row;
    }
    String recipient = recipientFor(entity.getAnswers(), upper);
    if (recipient == null || recipient.isBlank()) {
      row.put("status", "SKIPPED");
      row.put("error", "No recipient for channel " + upper);
      return row;
    }
    row.put("recipient", recipient);
    Map<String, Object> response =
        notificationDelivery.queue(scope.organizationId(), upper, recipient, subject, body);
    row.put("status", String.valueOf(response.getOrDefault("status", "UNKNOWN")));
    if (response.get("id") != null) {
      row.put("notificationId", response.get("id"));
    }
    if (response.get("error") != null) {
      row.put("error", response.get("error"));
    }
    return row;
  }

  private static String recipientFor(Map<String, Object> answers, String channel) {
    if (answers == null) {
      return null;
    }
    if ("EMAIL".equals(channel)) {
      Object email = answers.get("email");
      return email != null ? String.valueOf(email).trim() : null;
    }
    if ("SMS".equals(channel) || "WHATSAPP".equals(channel)) {
      Object mobile = answers.get("mobile");
      return mobile != null ? String.valueOf(mobile).trim() : null;
    }
    return null;
  }

  private List<String> resolveApproveChannels(
      Map<String, Object> settings, Map<String, Object> resolved) {
    Object configured = settings.get("approveNotificationChannels");
    if (configured instanceof List<?> list && !list.isEmpty()) {
      return list.stream().map(String::valueOf).toList();
    }
    Object fromTemplate = resolved.get("channels");
    if (fromTemplate instanceof List<?> list && !list.isEmpty()) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of("EMAIL", "IN_APP");
  }

  private void requireFeature(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FEATURE_HOSTEL)) {
      throw new HostelException(
          "FEATURE_DISABLED", "FEATURE_HOSTEL is off for this subscription plan.");
    }
  }

  private void requireModuleEnabled(TenantScope scope) {
    Map<String, Object> settings =
        moduleSettingsMap(engines.getModuleSettings(scope, MODULE_HOSTEL));
    if (Boolean.FALSE.equals(settings.get("enabled"))) {
      throw new HostelException(
          "MODULE_DISABLED", "Hostel module is disabled in module settings.");
    }
  }

  private String resolveFormKey(Map<String, Object> module) {
    Object configured = moduleSettingsMap(module).get("formKey");
    if (configured != null && !String.valueOf(configured).isBlank()) {
      return String.valueOf(configured);
    }
    return properties.getDefaults().getFormKey();
  }

  private String resolveWorkflowKey(Map<String, Object> module) {
    Object configured = moduleSettingsMap(module).get("workflowKey");
    if (configured != null && !String.valueOf(configured).isBlank()) {
      return String.valueOf(configured);
    }
    return properties.getDefaults().getWorkflowKey();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> moduleSettingsMap(Map<String, Object> module) {
    if (module == null) {
      return Map.of();
    }
    Object nested = module.get("settings");
    if (nested instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    return module;
  }

  private HostelRecordEntity requireRecord(UUID id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new HostelException("NOT_FOUND", "Record not found"));
  }

  private void validateMandatory(Map<String, Object> form, Map<String, Object> answers) {
    Object sectionsObj = form.get("sections");
    if (!(sectionsObj instanceof List<?> sections)) {
      return;
    }
    for (Object sectionObj : sections) {
      if (!(sectionObj instanceof Map<?, ?> section)) {
        continue;
      }
      Object fieldsObj = section.get("fields");
      if (!(fieldsObj instanceof List<?> fields)) {
        continue;
      }
      for (Object fieldObj : fields) {
        if (!(fieldObj instanceof Map<?, ?> field)) {
          continue;
        }
        if (!Boolean.TRUE.equals(field.get("mandatory"))) {
          continue;
        }
        String key = String.valueOf(field.get("key"));
        Object value = answers.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
          Object label = field.get("label");
          throw new HostelException(
              "VALIDATION", "Mandatory field missing: " + (label != null ? label : key));
        }
      }
    }
  }

  private List<WorkflowStep> steps(Map<String, Object> workflow) {
    Object stepsObj = workflow.get("steps");
    if (!(stepsObj instanceof List<?> list) || list.isEmpty()) {
      throw new HostelException("WORKFLOW_EMPTY", "Workflow has no steps configured");
    }
    List<WorkflowStep> steps = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> m = (Map<String, Object>) raw;
      int seq = Integer.parseInt(String.valueOf(m.getOrDefault("sequence", 0)));
      String name = String.valueOf(m.getOrDefault("name", "Step " + seq));
      String role = String.valueOf(m.getOrDefault("assignRole", "ADMIN"));
      steps.add(new WorkflowStep(seq, name, role));
    }
    steps.sort(Comparator.comparingInt(WorkflowStep::sequence));
    return steps;
  }

  private WorkflowStep firstStep(Map<String, Object> workflow) {
    return steps(workflow).get(0);
  }

  private Map<String, Object> recordNotification(
      TenantScope scope, HostelRecordEntity entity, String intent) {
    Map<String, Object> preview =
        engines.previewNotification(
            scope,
            "HOSTEL",
            List.of("IN_APP", "EMAIL"),
            intent + " recordId=" + entity.getId());
    Map<String, Object> stored = new LinkedHashMap<>();
    stored.put("intent", intent);
    stored.put("at", Instant.now().toString());
    stored.put("preview", preview);
    return stored;
  }

  private Map<String, Object> event(
      String type, String userId, String role, String message, int stepSeq, String stepName) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("type", type);
    e.put("at", Instant.now().toString());
    e.put("userId", userId);
    e.put("role", role);
    e.put("message", message);
    e.put("stepSequence", stepSeq);
    e.put("stepName", stepName);
    return e;
  }

  private Map<String, Object> toDto(HostelRecordEntity e) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", e.getId().toString());
    dto.put("organizationId", e.getOrganizationId());
    dto.put("branchId", e.getBranchId());
    dto.put("academicSessionId", e.getAcademicSessionId());
    dto.put("formKey", e.getFormKey());
    dto.put("workflowKey", e.getWorkflowKey());
    dto.put("status", e.getStatus());
    dto.put("currentStepSequence", e.getCurrentStepSequence());
    dto.put("currentStepName", e.getCurrentStepName());
    dto.put("assigneeRole", e.getAssigneeRole());
    dto.put("answers", e.getAnswers());
    dto.put("history", e.getHistory());
    dto.put("matchedActions", e.getMatchedActions());
    dto.put("notificationIntents", e.getNotificationIntents());
    dto.put("createdBy", e.getCreatedBy());
    dto.put("createdAt", e.getCreatedAt().toString());
    dto.put("updatedAt", e.getUpdatedAt().toString());
    return dto;
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? fallback : s;
  }

  private record WorkflowStep(int sequence, String name, String assignRole) {}
}
