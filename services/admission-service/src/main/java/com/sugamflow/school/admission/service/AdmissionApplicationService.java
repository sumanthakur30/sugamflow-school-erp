package com.sugamflow.school.admission.service;

import com.sugamflow.school.admission.config.AdmissionProperties;
import com.sugamflow.school.admission.integration.ConfigEngineClient;
import com.sugamflow.school.admission.integration.NotificationDeliveryClient;
import com.sugamflow.school.admission.integration.StudentEnrollmentClient;
import com.sugamflow.school.admission.persistence.entity.AdmissionApplicationEntity;
import com.sugamflow.school.admission.persistence.repo.AdmissionApplicationRepository;
import com.sugamflow.school.admission.web.AdmissionException;
import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
public class AdmissionApplicationService {

  public static final String FEATURE_ADMISSION = "FEATURE_ADMISSION";
  public static final String MODULE_ADMISSION = "admission";
  public static final String ACTION_BLOCK = "BLOCK_ADMISSION";
  public static final String ACTION_NOTIFY = "NOTIFY_ADMISSION";
  public static final String DOC_OFFER_LETTER = "OFFER_LETTER";
  public static final String DOC_ENROLLMENT = "ENROLLMENT";

  private final AdmissionApplicationRepository repository;
  private final ConfigEngineClient engines;
  private final NotificationDeliveryClient notificationDelivery;
  private final StudentEnrollmentClient studentEnrollment;
  private final AdmissionProperties properties;

  public AdmissionApplicationService(
      AdmissionApplicationRepository repository,
      ConfigEngineClient engines,
      NotificationDeliveryClient notificationDelivery,
      StudentEnrollmentClient studentEnrollment,
      AdmissionProperties properties) {
    this.repository = repository;
    this.engines = engines;
    this.notificationDelivery = notificationDelivery;
    this.studentEnrollment = studentEnrollment;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_ADMISSION);
    String formKey = resolveFormKey(module);
    String workflowKey = resolveWorkflowKey(module);

    Map<String, Object> form = engines.getForm(scope, formKey);
    Map<String, Object> workflow = engines.getWorkflow(scope, workflowKey);
    if (form == null) {
      throw new AdmissionException("FORM_MISSING", "Form definition not found: " + formKey);
    }
    if (workflow == null) {
      throw new AdmissionException("WORKFLOW_MISSING", "Workflow definition not found: " + workflowKey);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("module", module);
    out.put("formKey", formKey);
    out.put("workflowKey", workflowKey);
    out.put("form", form);
    out.put("workflow", workflow);
    return out;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    PageQuery q = PageQuery.of(page, size);
    Pageable pageable = PageRequest.of(q.page(), q.size());
    Page<AdmissionApplicationEntity> result;
    if (scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank()) {
      result =
          repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
              scope.organizationId(), scope.branchId(), scope.academicSessionId(), pageable);
    } else {
      result = repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId(), pageable);
    }
    return PageResult.of(
        result.map(this::toDto).getContent(), q.page(), q.size(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return toDto(requireApp(id, scope.organizationId()));
  }

  @Transactional
  public Map<String, Object> submit(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requireModuleEnabled(scope);

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_ADMISSION);
    String formKey =
        stringOr(body.get("formKey"), resolveFormKey(module));
    String workflowKey =
        stringOr(body.get("workflowKey"), resolveWorkflowKey(module));

    @SuppressWarnings("unchecked")
    Map<String, Object> answers =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();

    Map<String, Object> form = engines.getForm(scope, formKey);
    if (form == null) {
      throw new AdmissionException("FORM_MISSING", "Form definition not found: " + formKey);
    }
    validateMandatory(form, answers);

    Map<String, Object> workflow = engines.getWorkflow(scope, workflowKey);
    if (workflow == null) {
      throw new AdmissionException("WORKFLOW_MISSING", "Workflow definition not found: " + workflowKey);
    }

    Map<String, Object> ruleContext = new LinkedHashMap<>();
    ruleContext.put("application", answers);
    ruleContext.put("admission", Map.of("formKey", formKey, "workflowKey", workflowKey));
    List<String> matched = engines.evaluateRules(scope, ruleContext);
    if (matched.contains(ACTION_BLOCK)) {
      throw new AdmissionException(
          ACTION_BLOCK, "Admission blocked by rule engine (BLOCK_ADMISSION).");
    }

    WorkflowStep first = firstStep(workflow);

    AdmissionApplicationEntity entity = new AdmissionApplicationEntity();
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
            "Application submitted",
            first.sequence(),
            first.name()));
    entity.setHistory(history);

    List<Map<String, Object>> intents = new ArrayList<>();
    intents.add(recordNotification(scope, entity, "ADMISSION_SUBMITTED"));
    if (matched.contains(ACTION_NOTIFY)) {
      intents.add(recordNotification(scope, entity, "ADMISSION_RULE_NOTIFY"));
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

    AdmissionApplicationEntity entity = requireApp(id, scope.organizationId());
    if ("REJECTED".equals(entity.getStatus()) || "APPROVED".equals(entity.getStatus())) {
      throw new AdmissionException("TERMINAL", "Application is already " + entity.getStatus());
    }

    Map<String, Object> workflow = engines.getWorkflow(scope, entity.getWorkflowKey());
    if (workflow == null) {
      throw new AdmissionException(
          "WORKFLOW_MISSING", "Workflow definition not found: " + entity.getWorkflowKey());
    }

    return switch (action) {
      case "REJECT" -> reject(entity, scope, comment);
      case "REQUEST_INFO" -> requestInfo(entity, scope, comment);
      case "APPROVE" -> approve(entity, scope, workflow, comment);
      default -> throw new AdmissionException(
          "UNKNOWN_ACTION", "Supported actions: APPROVE, REJECT, REQUEST_INFO");
    };
  }

  private Map<String, Object> reject(
      AdmissionApplicationEntity entity, TenantScope scope, String comment) {
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
    entity.getNotificationIntents().add(recordNotification(scope, entity, "ADMISSION_REJECTED"));
    return toDto(repository.save(entity));
  }

  private Map<String, Object> requestInfo(
      AdmissionApplicationEntity entity, TenantScope scope, String comment) {
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
    entity.getNotificationIntents().add(recordNotification(scope, entity, "ADMISSION_INFO_REQUESTED"));
    return toDto(repository.save(entity));
  }

  private Map<String, Object> approve(
      AdmissionApplicationEntity entity,
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
                    new AdmissionException(
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

    if (next == null || "SYSTEM".equalsIgnoreCase(next.assignRole()) || "Completed".equalsIgnoreCase(next.name())) {
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
                  "Admission approved",
                  entity.getCurrentStepSequence(),
                  entity.getCurrentStepName()));
      onFinalApprove(entity, scope);
    } else {
      entity.setStatus("IN_PROGRESS");
      entity.setCurrentStepSequence(next.sequence());
      entity.setCurrentStepName(next.name());
      entity.setAssigneeRole(next.assignRole());
      entity.getNotificationIntents()
          .add(recordNotification(scope, entity, "ADMISSION_STEP_ADVANCED"));
    }

    entity.setUpdatedAt(Instant.now());
    return toDto(repository.save(entity));
  }

  @Transactional(readOnly = true)
  public byte[] getOfferLetterPdf(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    AdmissionApplicationEntity entity = requireApp(id, scope.organizationId());
    Map<String, Object> doc = findOfferLetter(entity);
    if (doc == null || doc.get("contentBase64") == null) {
      throw new AdmissionException("OFFER_LETTER_MISSING", "Offer letter not generated for this application");
    }
    try {
      return Base64.getDecoder().decode(String.valueOf(doc.get("contentBase64")));
    } catch (IllegalArgumentException ex) {
      throw new AdmissionException("OFFER_LETTER_CORRUPT", "Stored offer letter is not valid base64");
    }
  }

  private void onFinalApprove(AdmissionApplicationEntity entity, TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_ADMISSION);
    Map<String, Object> settings = moduleSettingsMap(module);
    String offerUrl = offerLetterUrl(entity.getId());
    Map<String, Object> offerDoc = renderOfferLetter(scope, entity, settings, offerUrl);
    if (offerDoc != null) {
      entity.getDocuments().removeIf(d -> DOC_OFFER_LETTER.equals(String.valueOf(d.get("type"))));
      entity.getDocuments().add(offerDoc);
    }
    boolean enroll =
        settings.get("enrollOnApprove") == null
            || Boolean.TRUE.equals(settings.get("enrollOnApprove"));
    if (enroll) {
      Map<String, Object> enrollment = enrollStudent(scope, entity, settings);
      entity.getDocuments().removeIf(d -> DOC_ENROLLMENT.equals(String.valueOf(d.get("type"))));
      entity.getDocuments().add(enrollment);
      entity
          .getHistory()
          .add(
              event(
                  "ENROLLED",
                  scope.userId(),
                  scope.roleCode(),
                  enrollmentMessage(enrollment),
                  entity.getCurrentStepSequence(),
                  entity.getCurrentStepName()));
    }
    boolean notify =
        settings.get("notifyOnApprove") == null
            || Boolean.TRUE.equals(settings.get("notifyOnApprove"));
    Map<String, Object> intent =
        notify
            ? deliverApproveNotifications(scope, entity, settings, offerUrl)
            : recordNotification(scope, entity, "ADMISSION_APPROVED");
    entity.getNotificationIntents().add(intent);
  }

  private Map<String, Object> enrollStudent(
      TenantScope scope, AdmissionApplicationEntity entity, Map<String, Object> settings) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("applicationId", entity.getId().toString());
    payload.put("answers", entity.getAnswers());
    payload.put(
        "formKey", stringOr(settings.get("studentFormKey"), "student_master"));
    if (settings.get("admissionFieldMap") instanceof Map<?, ?> map) {
      payload.put("fieldMap", map);
    }
    Map<String, Object> result = studentEnrollment.enrollFromAdmission(scope, payload);
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("type", DOC_ENROLLMENT);
    doc.put("at", Instant.now().toString());
    if (result == null) {
      doc.put("status", "FAILED");
      doc.put("error", "Empty enrollment response");
      return doc;
    }
    if ("FAILED".equals(String.valueOf(result.get("status"))) && result.get("id") == null) {
      doc.put("status", "FAILED");
      doc.put("error", result.getOrDefault("error", "Enrollment failed"));
      return doc;
    }
    doc.put("status", "ENROLLED");
    doc.put("studentId", result.get("id"));
    doc.put("admissionNo", result.get("admissionNo"));
    doc.put("formKey", result.get("formKey"));
    return doc;
  }

  private static String enrollmentMessage(Map<String, Object> enrollment) {
    if ("ENROLLED".equals(String.valueOf(enrollment.get("status")))) {
      return "Student enrolled: "
          + enrollment.get("studentId")
          + (enrollment.get("admissionNo") != null
              ? " (" + enrollment.get("admissionNo") + ")"
              : "");
    }
    return "Student enrollment failed: " + enrollment.getOrDefault("error", "unknown");
  }

  private static Map<String, Object> findEnrollment(AdmissionApplicationEntity entity) {
    for (Map<String, Object> doc : entity.getDocuments()) {
      if (DOC_ENROLLMENT.equals(String.valueOf(doc.get("type")))
          && "ENROLLED".equals(String.valueOf(doc.get("status")))) {
        return doc;
      }
    }
    return null;
  }

  private Map<String, Object> renderOfferLetter(
      TenantScope scope,
      AdmissionApplicationEntity entity,
      Map<String, Object> settings,
      String offerUrl) {
    String templateKey =
        stringOr(
            settings.get("offerLetterTemplateKey"),
            properties.getDefaults().getOfferLetterTemplateKey());
    if (templateKey == null || templateKey.isBlank()) {
      return null;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("application", entity.getAnswers());
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", scope.organizationId());
    context.put("branchId", scope.branchId());
    context.put("academicSessionId", scope.academicSessionId());
    context.put("applicationId", entity.getId().toString());
    context.put("offerLetterUrl", offerUrl);
    context.put("issuedAt", Instant.now().toString());
    data.put("context", context);

    Map<String, Object> rendered = engines.renderReport(scope, templateKey, data);
    if (rendered == null || rendered.get("contentBase64") == null) {
      Map<String, Object> failed = new LinkedHashMap<>();
      failed.put("type", DOC_OFFER_LETTER);
      failed.put("templateKey", templateKey);
      failed.put("status", "FAILED");
      failed.put("error", "Report render returned no PDF");
      failed.put("renderedAt", Instant.now().toString());
      return failed;
    }
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("type", DOC_OFFER_LETTER);
    doc.put("templateKey", templateKey);
    doc.put("status", "READY");
    doc.put("fileName", rendered.getOrDefault("fileName", templateKey + ".pdf"));
    doc.put("contentType", rendered.getOrDefault("contentType", "application/pdf"));
    doc.put("contentBase64", rendered.get("contentBase64"));
    doc.put("byteLength", rendered.get("byteLength"));
    doc.put("renderedAt", Instant.now().toString());
    doc.put("downloadPath", "/api/admission/applications/" + entity.getId() + "/offer-letter");
    return doc;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> deliverApproveNotifications(
      TenantScope scope,
      AdmissionApplicationEntity entity,
      Map<String, Object> settings,
      String offerUrl) {
    String intent = "ADMISSION_APPROVED";
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("application", entity.getAnswers());
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", scope.organizationId());
    context.put("branchId", scope.branchId());
    context.put("academicSessionId", scope.academicSessionId());
    context.put("applicationId", entity.getId().toString());
    context.put("offerLetterUrl", offerUrl);
    variables.put("context", context);

    String templateId =
        stringOr(settings.get("approveNotificationTemplateId"), "admission_approved");
    Map<String, Object> resolved =
        engines.resolveNotification(scope, "ADMISSION", templateId, variables);
    if (resolved.isEmpty() || !Boolean.TRUE.equals(resolved.get("resolved"))) {
      resolved = engines.resolveNotification(scope, "ADMISSION", intent, variables);
    }

    List<String> channels = resolveApproveChannels(settings, resolved);
    String subject = String.valueOf(resolved.getOrDefault("subject", "Admission approved"));
    String body = String.valueOf(resolved.getOrDefault("body", "Admission approved. " + offerUrl));

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
      AdmissionApplicationEntity entity,
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
    if ("PUSH".equals(channel)) {
      Object user = answers.get("userId");
      return user != null ? String.valueOf(user).trim() : null;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
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

  private String offerLetterUrl(UUID applicationId) {
    String base = properties.getIntegrations().getPublicApiBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/api/admission/applications/" + applicationId + "/offer-letter";
  }

  private static Map<String, Object> findOfferLetter(AdmissionApplicationEntity entity) {
    for (Map<String, Object> doc : entity.getDocuments()) {
      if (DOC_OFFER_LETTER.equals(String.valueOf(doc.get("type")))
          && "READY".equals(String.valueOf(doc.get("status")))) {
        return doc;
      }
    }
    return null;
  }

  private void requireFeature(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FEATURE_ADMISSION)) {
      throw new AdmissionException(
          "FEATURE_DISABLED", "FEATURE_ADMISSION is off for this subscription plan.");
    }
  }

  private void requireModuleEnabled(TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_ADMISSION);
    Map<String, Object> settings = moduleSettingsMap(module);
    Object enabled = settings.get("enabled");
    if (enabled != null && Boolean.FALSE.equals(enabled)) {
      throw new AdmissionException(
          "MODULE_DISABLED", "Admission module is disabled in module settings.");
    }
  }

  private String resolveFormKey(Map<String, Object> module) {
    Map<String, Object> settings = moduleSettingsMap(module);
    Object configured = settings.get("formKey");
    if (configured != null && !String.valueOf(configured).isBlank()) {
      return String.valueOf(configured);
    }
    return properties.getDefaults().getFormKey();
  }

  private String resolveWorkflowKey(Map<String, Object> module) {
    Map<String, Object> settings = moduleSettingsMap(module);
    Object configured = settings.get("workflowKey");
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

  private AdmissionApplicationEntity requireApp(UUID id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new AdmissionException("NOT_FOUND", "Application not found"));
  }

  @SuppressWarnings("unchecked")
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
          throw new AdmissionException(
              "VALIDATION", "Mandatory field missing: " + (label != null ? label : key));
        }
      }
    }
  }

  private List<WorkflowStep> steps(Map<String, Object> workflow) {
    Object stepsObj = workflow.get("steps");
    if (!(stepsObj instanceof List<?> list) || list.isEmpty()) {
      throw new AdmissionException("WORKFLOW_EMPTY", "Workflow has no steps configured");
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
      TenantScope scope, AdmissionApplicationEntity entity, String intent) {
    Map<String, Object> preview =
        engines.previewNotification(
            scope,
            "ADMISSION",
            List.of("IN_APP", "EMAIL"),
            intent + " applicationId=" + entity.getId());
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

  private Map<String, Object> toDto(AdmissionApplicationEntity e) {
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
    dto.put("documents", documentsForDto(e));
    dto.put("hasOfferLetter", findOfferLetter(e) != null);
    Map<String, Object> enrollment = findEnrollment(e);
    dto.put("hasEnrollment", enrollment != null);
    dto.put("enrolledStudentId", enrollment != null ? enrollment.get("studentId") : null);
    dto.put("enrolledAdmissionNo", enrollment != null ? enrollment.get("admissionNo") : null);
    dto.put("createdBy", e.getCreatedBy());
    dto.put("createdAt", e.getCreatedAt().toString());
    dto.put("updatedAt", e.getUpdatedAt().toString());
    return dto;
  }

  private static List<Map<String, Object>> documentsForDto(AdmissionApplicationEntity e) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> doc : e.getDocuments()) {
      Map<String, Object> slim = new LinkedHashMap<>(doc);
      slim.remove("contentBase64");
      out.add(slim);
    }
    return out;
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
