package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.api.PageResults;
import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.fee.config.FeeProperties;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.integration.NotificationDeliveryClient;
import com.sugamflow.school.fee.integration.StudentAccessClient;
import com.sugamflow.school.fee.integration.StudentProfileClient;
import com.sugamflow.school.fee.persistence.entity.FeeCollectionEntity;
import com.sugamflow.school.fee.persistence.repo.FeeCollectionRepository;
import com.sugamflow.school.fee.web.FeeException;
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
public class FeeCollectionService {

  public static final String FEATURE_FEE = "FEATURE_FEE";
  public static final String MODULE_FEE = "fee";
  public static final String ACTION_BLOCK = "BLOCK_FEE";
  public static final String ACTION_NOTIFY = "NOTIFY_FEE";
  public static final String DOC_FEE_RECEIPT = "FEE_RECEIPT";

  private final FeeCollectionRepository repository;
  private final ConfigEngineClient engines;
  private final NotificationDeliveryClient notificationDelivery;
  private final FeeProperties properties;
  private final FinanceService financeService;
  private final StudentAccessClient studentAccess;
  private final StudentProfileClient studentProfile;

  public FeeCollectionService(
      FeeCollectionRepository repository,
      ConfigEngineClient engines,
      NotificationDeliveryClient notificationDelivery,
      FeeProperties properties,
      FinanceService financeService,
      StudentAccessClient studentAccess,
      StudentProfileClient studentProfile) {
    this.repository = repository;
    this.engines = engines;
    this.notificationDelivery = notificationDelivery;
    this.properties = properties;
    this.financeService = financeService;
    this.studentAccess = studentAccess;
    this.studentProfile = studentProfile;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_FEE);
    String formKey = resolveFormKey(module);
    String workflowKey = resolveWorkflowKey(module);
    Map<String, Object> form = engines.getForm(scope, formKey);
    Map<String, Object> workflow = engines.getWorkflow(scope, workflowKey);
    if (form == null) {
      throw new FeeException("FORM_MISSING", "Form definition not found: " + formKey);
    }
    if (workflow == null) {
      throw new FeeException("WORKFLOW_MISSING", "Workflow definition not found: " + workflowKey);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("module", module);
    out.put("formKey", formKey);
    out.put("workflowKey", workflowKey);
    out.put("form", form);
    out.put("workflow", workflow);
    try {
      out.put("finance", financeService.bootstrap());
    } catch (Exception ignored) {
      out.put("finance", Map.of("featureEnabled", true, "heads", List.of(), "structures", List.of()));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    PageQuery q = PageQuery.of(page, size);
    AccessScope access = studentAccess.resolve(scope);

    if (access.restricted()) {
      List<FeeCollectionEntity> all = loadCandidates(scope);
      List<Map<String, Object>> dtos = new ArrayList<>();
      for (FeeCollectionEntity e : all) {
        Map<String, Object> dto = toDto(e);
        if (access.allowsStudentDto(dto)) {
          dtos.add(dto);
        }
      }
      return PageResults.filterThenPage(dtos, d -> true, q.page(), q.size());
    }

    Pageable pageable = PageRequest.of(q.page(), q.size());
    Page<FeeCollectionEntity> result;
    if (scope.branchId() != null && !scope.branchId().isBlank()
        && scope.academicSessionId() != null && !scope.academicSessionId().isBlank()) {
      result = repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId(), pageable);
    } else {
      result = repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId(), pageable);
    }
    return PageResult.of(result.map(this::toDto).getContent(), q.page(), q.size(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    Map<String, Object> dto = toDetailDto(requireCollection(id, scope.organizationId()));
    AccessScope access = studentAccess.resolve(scope);
    if (!access.allowsStudentDto(dto)) {
      throw new FeeException("NOT_FOUND", "Fee collection not found");
    }
    return dto;
  }

  @Transactional
  public Map<String, Object> submit(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new FeeException("FORBIDDEN", ex.getMessage());
    }
    requireFeature(scope);
    requireModuleEnabled(scope);

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_FEE);
    String formKey = stringOr(body.get("formKey"), resolveFormKey(module));
    String workflowKey = stringOr(body.get("workflowKey"), resolveWorkflowKey(module));

    @SuppressWarnings("unchecked")
    Map<String, Object> answers =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();

    Map<String, Object> form = engines.getForm(scope, formKey);
    if (form == null) {
      throw new FeeException("FORM_MISSING", "Form definition not found: " + formKey);
    }
    validateMandatory(form, answers);

    Map<String, Object> workflow = engines.getWorkflow(scope, workflowKey);
    if (workflow == null) {
      throw new FeeException("WORKFLOW_MISSING", "Workflow definition not found: " + workflowKey);
    }

    Map<String, Object> ruleContext = new LinkedHashMap<>();
    ruleContext.put("payment", answers);
    ruleContext.put("fee", Map.of("formKey", formKey, "workflowKey", workflowKey));
    List<String> matched = engines.evaluateRules(scope, ruleContext);
    if (matched.contains(ACTION_BLOCK)) {
      throw new FeeException(ACTION_BLOCK, "Fee collection blocked by rule engine (BLOCK_FEE).");
    }

    WorkflowStep first = firstStep(workflow);
    FeeCollectionEntity entity = new FeeCollectionEntity();
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
            "Fee collection submitted",
            first.sequence(),
            first.name()));
    entity.setHistory(history);

    List<Map<String, Object>> intents = new ArrayList<>();
    intents.add(recordNotification(scope, entity, "FEE_SUBMITTED"));
    if (matched.contains(ACTION_NOTIFY)) {
      intents.add(recordNotification(scope, entity, "FEE_RULE_NOTIFY"));
    }
    entity.setNotificationIntents(intents);
    return toDetailDto(repository.save(entity));
  }

  /** Isolated submit so demand generation is not rolled back when collection validation fails. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public Map<String, Object> submitIsolated(Map<String, Object> body) {
    return submit(body);
  }

  @Transactional
  public Map<String, Object> act(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    String action = String.valueOf(body.getOrDefault("action", "")).trim().toUpperCase();
    String comment = body.get("comment") != null ? String.valueOf(body.get("comment")) : null;
    FeeCollectionEntity entity = requireCollection(id, scope.organizationId());
    if ("REJECTED".equals(entity.getStatus()) || "APPROVED".equals(entity.getStatus())) {
      throw new FeeException("TERMINAL", "Collection is already " + entity.getStatus());
    }
    Map<String, Object> workflow = engines.getWorkflow(scope, entity.getWorkflowKey());
    if (workflow == null) {
      throw new FeeException(
          "WORKFLOW_MISSING", "Workflow definition not found: " + entity.getWorkflowKey());
    }
    return switch (action) {
      case "REJECT" -> reject(entity, scope, comment);
      case "REQUEST_INFO" -> requestInfo(entity, scope, comment);
      case "APPROVE" -> approve(entity, scope, workflow, comment);
      default -> throw new FeeException(
          "UNKNOWN_ACTION", "Supported actions: APPROVE, REJECT, REQUEST_INFO");
    };
  }

  private Map<String, Object> reject(FeeCollectionEntity entity, TenantScope scope, String comment) {
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
    entity.getNotificationIntents().add(recordNotification(scope, entity, "FEE_REJECTED"));
    return toDetailDto(repository.save(entity));
  }

  private Map<String, Object> requestInfo(
      FeeCollectionEntity entity, TenantScope scope, String comment) {
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
    entity.getNotificationIntents().add(recordNotification(scope, entity, "FEE_INFO_REQUESTED"));
    return toDetailDto(repository.save(entity));
  }

  private Map<String, Object> approve(
      FeeCollectionEntity entity,
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
                    new FeeException(
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
                  "Fee collection approved",
                  entity.getCurrentStepSequence(),
                  entity.getCurrentStepName()));
      onFinalApprove(entity, scope);
    } else {
      entity.setStatus("IN_PROGRESS");
      entity.setCurrentStepSequence(next.sequence());
      entity.setCurrentStepName(next.name());
      entity.setAssigneeRole(next.assignRole());
      entity.getNotificationIntents().add(recordNotification(scope, entity, "FEE_STEP_ADVANCED"));
    }
    entity.setUpdatedAt(Instant.now());
    return toDetailDto(repository.save(entity));
  }

  @Transactional(readOnly = true)
  public byte[] getFeeReceiptPdf(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    FeeCollectionEntity entity = requireCollection(id, scope.organizationId());
    Map<String, Object> doc = findFeeReceipt(entity);
    if (doc == null || doc.get("contentBase64") == null) {
      throw new FeeException("FEE_RECEIPT_MISSING", "Fee receipt not generated for this collection");
    }
    try {
      return Base64.getDecoder().decode(String.valueOf(doc.get("contentBase64")));
    } catch (IllegalArgumentException ex) {
      throw new FeeException("FEE_RECEIPT_CORRUPT", "Stored fee receipt is not valid base64");
    }
  }

  /**
   * Gateway settlement: stamp ONLINE payment details and force APPROVED + receipt when the
   * collection is still open. Idempotent if already APPROVED.
   */
  @Transactional
  public Map<String, Object> markPaidFromGateway(
      UUID collectionId, String gatewayTxnId, String receiptRef, String providerKey) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    FeeCollectionEntity entity = requireCollection(collectionId, scope.organizationId());
    Map<String, Object> answers =
        entity.getAnswers() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(entity.getAnswers());
    answers.put("paymentMode", "ONLINE");
    if (providerKey != null && !providerKey.isBlank()) {
      answers.put("paymentProvider", providerKey);
    }
    if (gatewayTxnId != null && !gatewayTxnId.isBlank()) {
      answers.put("gatewayTxnId", gatewayTxnId);
    }
    if (receiptRef != null && !receiptRef.isBlank()) {
      answers.put("receiptRef", receiptRef);
    }
    entity.setAnswers(answers);

    if ("APPROVED".equals(entity.getStatus())) {
      entity.setUpdatedAt(Instant.now());
      return toDto(repository.save(entity));
    }
    if ("REJECTED".equals(entity.getStatus())) {
      throw new FeeException("TERMINAL", "Cannot mark a rejected collection as paid");
    }

    entity.setStatus("APPROVED");
    entity.setUpdatedAt(Instant.now());
    entity
        .getHistory()
        .add(
            event(
                "GATEWAY_PAID",
                scope.userId() == null ? "gateway" : scope.userId(),
                scope.roleCode() == null ? "SYSTEM" : scope.roleCode(),
                "Paid online via " + (providerKey == null ? "gateway" : providerKey),
                entity.getCurrentStepSequence(),
                entity.getCurrentStepName()));
    entity
        .getHistory()
        .add(
            event(
                "APPROVED",
                "gateway",
                "SYSTEM",
                "Fee collection approved after online capture",
                entity.getCurrentStepSequence(),
                entity.getCurrentStepName()));
    onFinalApprove(entity, scope);
    return toDto(repository.save(entity));
  }

  private void onFinalApprove(FeeCollectionEntity entity, TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_FEE);
    Map<String, Object> settings = moduleSettingsMap(module);
    String receiptUrl = feeReceiptUrl(entity.getId());
    Map<String, Object> receiptDoc = renderFeeReceipt(scope, entity, settings, receiptUrl);
    if (receiptDoc != null) {
      entity.getDocuments().removeIf(d -> DOC_FEE_RECEIPT.equals(String.valueOf(d.get("type"))));
      entity.getDocuments().add(receiptDoc);
    }
    boolean notify =
        settings.get("notifyOnApprove") == null
            || Boolean.TRUE.equals(settings.get("notifyOnApprove"));
    Map<String, Object> intent =
        notify
            ? deliverApproveNotifications(scope, entity, settings, receiptUrl)
            : recordNotification(scope, entity, "FEE_APPROVED");
    entity.getNotificationIntents().add(intent);
  }

  private Map<String, Object> renderFeeReceipt(
      TenantScope scope,
      FeeCollectionEntity entity,
      Map<String, Object> settings,
      String receiptUrl) {
    String templateKey =
        stringOr(
            settings.get("feeReceiptTemplateKey"),
            properties.getDefaults().getFeeReceiptTemplateKey());
    if (templateKey == null || templateKey.isBlank()) {
      return null;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("payment", entity.getAnswers());
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", scope.organizationId());
    context.put("branchId", scope.branchId());
    context.put("academicSessionId", scope.academicSessionId());
    context.put("collectionId", entity.getId().toString());
    context.put("feeReceiptUrl", receiptUrl);
    context.put("issuedAt", Instant.now().toString());
    data.put("context", context);

    Map<String, Object> rendered = engines.renderReport(scope, templateKey, data);
    if (rendered == null || rendered.get("contentBase64") == null) {
      Map<String, Object> failed = new LinkedHashMap<>();
      failed.put("type", DOC_FEE_RECEIPT);
      failed.put("templateKey", templateKey);
      failed.put("status", "FAILED");
      failed.put("error", "Report render returned no PDF");
      failed.put("renderedAt", Instant.now().toString());
      return failed;
    }
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("type", DOC_FEE_RECEIPT);
    doc.put("templateKey", templateKey);
    doc.put("status", "READY");
    doc.put("fileName", rendered.getOrDefault("fileName", templateKey + ".pdf"));
    doc.put("contentType", rendered.getOrDefault("contentType", "application/pdf"));
    doc.put("contentBase64", rendered.get("contentBase64"));
    doc.put("byteLength", rendered.get("byteLength"));
    doc.put("renderedAt", Instant.now().toString());
    doc.put("downloadPath", "/api/fee/collections/" + entity.getId() + "/receipt");
    return doc;
  }

  private Map<String, Object> deliverApproveNotifications(
      TenantScope scope,
      FeeCollectionEntity entity,
      Map<String, Object> settings,
      String receiptUrl) {
    String intent = "FEE_APPROVED";
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("payment", entity.getAnswers());
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", scope.organizationId());
    context.put("branchId", scope.branchId());
    context.put("academicSessionId", scope.academicSessionId());
    context.put("collectionId", entity.getId().toString());
    context.put("feeReceiptUrl", receiptUrl);
    variables.put("context", context);

    String templateId = stringOr(settings.get("approveNotificationTemplateId"), "fee_approved");
    Map<String, Object> resolved =
        engines.resolveNotification(scope, "FEES", templateId, variables);
    if (resolved.isEmpty() || !Boolean.TRUE.equals(resolved.get("resolved"))) {
      resolved = engines.resolveNotification(scope, "FEES", intent, variables);
    }

    List<String> channels = resolveApproveChannels(settings, resolved);
    String subject = String.valueOf(resolved.getOrDefault("subject", "Fee receipt"));
    String body = String.valueOf(resolved.getOrDefault("body", "Fee approved. " + receiptUrl));

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
      FeeCollectionEntity entity,
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

  private String feeReceiptUrl(UUID collectionId) {
    String base = properties.getIntegrations().getPublicApiBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + "/api/fee/collections/" + collectionId + "/receipt";
  }

  private static Map<String, Object> findFeeReceipt(FeeCollectionEntity entity) {
    for (Map<String, Object> doc : entity.getDocuments()) {
      if (DOC_FEE_RECEIPT.equals(String.valueOf(doc.get("type")))
          && "READY".equals(String.valueOf(doc.get("status")))) {
        return doc;
      }
    }
    return null;
  }

  private void requireFeature(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FEATURE_FEE)) {
      throw new FeeException("FEATURE_DISABLED", "FEATURE_FEE is off for this subscription plan.");
    }
  }

  private void requireModuleEnabled(TenantScope scope) {
    Map<String, Object> settings = moduleSettingsMap(engines.getModuleSettings(scope, MODULE_FEE));
    if (Boolean.FALSE.equals(settings.get("enabled"))) {
      throw new FeeException("MODULE_DISABLED", "Fee module is disabled in module settings.");
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

  private List<FeeCollectionEntity> loadCandidates(TenantScope scope) {
    if (scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank()) {
      return repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId());
    }
    return repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId());
  }

  private FeeCollectionEntity requireCollection(UUID id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new FeeException("NOT_FOUND", "Collection not found"));
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
          throw new FeeException(
              "VALIDATION", "Mandatory field missing: " + (label != null ? label : key));
        }
      }
    }
  }

  private List<WorkflowStep> steps(Map<String, Object> workflow) {
    Object stepsObj = workflow.get("steps");
    if (!(stepsObj instanceof List<?> list) || list.isEmpty()) {
      throw new FeeException("WORKFLOW_EMPTY", "Workflow has no steps configured");
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
      TenantScope scope, FeeCollectionEntity entity, String intent) {
    Map<String, Object> preview =
        engines.previewNotification(
            scope, "FEES", List.of("IN_APP", "EMAIL"), intent + " collectionId=" + entity.getId());
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

  private Map<String, Object> toDto(FeeCollectionEntity e) {
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
    dto.put("hasFeeReceipt", findFeeReceipt(e) != null);
    dto.put("createdBy", e.getCreatedBy());
    dto.put("createdAt", e.getCreatedAt().toString());
    dto.put("updatedAt", e.getUpdatedAt().toString());
    return dto;
  }

  /** Detail payload includes live Student Master identity (no duplicated student storage). */
  private Map<String, Object> toDetailDto(FeeCollectionEntity e) {
    Map<String, Object> dto = toDto(e);
    attachStudentIdentity(dto, e);
    return dto;
  }

  private void attachStudentIdentity(Map<String, Object> dto, FeeCollectionEntity e) {
    Map<String, Object> answers = e.getAnswers() != null ? e.getAnswers() : Map.of();
    String admissionNo = stringOr(answers.get("admissionNo"), "");
    Map<String, Object> student = Map.of();
    if (!admissionNo.isBlank()) {
      student = studentProfile.identityByAdmissionNo(TenantContext.require(), admissionNo);
    }
    if (student == null || student.isEmpty()) {
      student = identityFromAnswers(e, answers);
    } else {
      // Fill gaps from fee answers when master field is blank (legacy collections).
      student = new LinkedHashMap<>(student);
      mergeBlank(student, "fullName", stringOr(answers.get("studentName"), ""));
      mergeBlank(student, "mobile", stringOr(answers.get("mobile"), ""));
      mergeBlank(student, "email", stringOr(answers.get("email"), ""));
      mergeBlank(student, "admissionNo", admissionNo);
    }
    dto.put("student", student);
    dto.put("studentId", student.get("id"));
    dto.put(
        "displayTitle",
        buildDisplayTitle(student, stringOr(answers.get("studentName"), "Collection")));
  }

  private static Map<String, Object> identityFromAnswers(
      FeeCollectionEntity e, Map<String, Object> answers) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", null);
    out.put("admissionNo", stringOr(answers.get("admissionNo"), ""));
    out.put("fullName", stringOr(answers.get("studentName"), ""));
    out.put("rollNo", stringOr(answers.get("rollNo"), ""));
    out.put(
        "classSection",
        stringOr(answers.get("classSection"), stringOr(answers.get("classApplied"), "")));
    out.put("className", "");
    out.put("section", "");
    out.put("fatherName", stringOr(answers.get("fatherName"), stringOr(answers.get("parentName"), "")));
    out.put("motherName", stringOr(answers.get("motherName"), ""));
    out.put("guardianName", stringOr(answers.get("guardianName"), ""));
    out.put("mobile", stringOr(answers.get("mobile"), ""));
    out.put("email", stringOr(answers.get("email"), ""));
    out.put("photoUrl", "");
    out.put("status", "");
    out.put("branchId", e.getBranchId());
    out.put("academicSessionId", e.getAcademicSessionId());
    out.put("fromAnswersOnly", true);
    return out;
  }

  private static void mergeBlank(Map<String, Object> target, String key, String value) {
    Object existing = target.get(key);
    if ((existing == null || String.valueOf(existing).isBlank()) && value != null && !value.isBlank()) {
      target.put(key, value);
    }
  }

  private static String buildDisplayTitle(Map<String, Object> student, String fallbackName) {
    String adm = stringOr(student.get("admissionNo"), "");
    String name = stringOr(student.get("fullName"), fallbackName);
    String cls =
        stringOr(
            student.get("classSection"),
            stringOr(student.get("className"), ""));
    if (!adm.isBlank() && !cls.isBlank()) {
      return adm + " — " + name + " (" + cls + ")";
    }
    if (!adm.isBlank()) {
      return adm + " — " + name;
    }
    return name;
  }

  private static List<Map<String, Object>> documentsForDto(FeeCollectionEntity e) {
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
