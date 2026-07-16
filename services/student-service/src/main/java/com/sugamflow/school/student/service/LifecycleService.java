package com.sugamflow.school.student.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.lifecycle.LifecycleCatalog;
import com.sugamflow.school.student.lifecycle.TcClearanceService;
import com.sugamflow.school.student.persistence.entity.LifecycleDefinitionEntity;
import com.sugamflow.school.student.persistence.entity.LifecycleEventEntity;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.LifecycleDefinitionRepository;
import com.sugamflow.school.student.persistence.repo.LifecycleEventRepository;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifecycleService {

  private final LifecycleDefinitionRepository definitionRepo;
  private final LifecycleEventRepository eventRepo;
  private final StudentRecordRepository studentRepo;
  private final ConfigEngineClient engines;

  public LifecycleService(
      LifecycleDefinitionRepository definitionRepo,
      LifecycleEventRepository eventRepo,
      StudentRecordRepository studentRepo,
      ConfigEngineClient engines) {
    this.definitionRepo = definitionRepo;
    this.eventRepo = eventRepo;
    this.studentRepo = studentRepo;
    this.engines = engines;
  }

  @Transactional
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    ensureDefaults(scope);
    Map<String, Object> module = engines.getModuleSettings(scope, StudentRecordService.MODULE_STUDENT);
    Map<String, Object> settings = moduleSettingsMap(module);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("reportBuilderEnabled", engines.isFeatureEnabled(scope, "FEATURE_REPORT_BUILDER"));
    out.put("settings", settings);
    out.put("promotionMaps", listDefinitions(LifecycleCatalog.TYPE_PROMOTION_MAP));
    out.put("statusPolicies", listDefinitions(LifecycleCatalog.TYPE_STATUS_POLICY));
    out.put("tcPolicies", listDefinitions(LifecycleCatalog.TYPE_TC_POLICY));
    out.put("sessions", listDefinitions(LifecycleCatalog.TYPE_ACADEMIC_SESSION));
    out.put("classFieldKey", stringOr(settings.get("classFieldKey"), "classApplied"));
    out.put("tcTemplateKey", stringOr(settings.get("tcTemplateKey"), "transfer_certificate"));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listDefinitions(String type) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    return definitionRepo
        .findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
            scope.organizationId(), type, "ACTIVE")
        .stream()
        .map(this::toDefinitionDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> saveDefinition(String type, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    String key = stringOr(body.get("definitionKey"), stringOr(body.get("code"), null));
    if (key == null || key.isBlank()) {
      throw new StudentException("VALIDATION", "definitionKey is required");
    }
    key = key.trim();
    Map<String, Object> payload = new LinkedHashMap<>(body);
    payload.put("definitionKey", key);
    payload.remove("id");
    payload.remove("version");

    LifecycleDefinitionEntity existing =
        definitionRepo
            .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                scope.organizationId(), type, key, "ACTIVE")
            .orElse(null);

    LifecycleDefinitionEntity entity = new LifecycleDefinitionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setDefinitionType(type);
    entity.setDefinitionKey(key);
    entity.setStatus("ACTIVE");
    entity.setVersion(existing == null ? 1 : existing.getVersion() + 1);
    entity.setPayload(payload);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    if (existing != null) {
      existing.setStatus("SUPERSEDED");
      existing.setUpdatedAt(Instant.now());
      definitionRepo.save(existing);
    }
    return toDefinitionDto(definitionRepo.save(entity));
  }

  @Transactional
  public Map<String, Object> promoteByClass(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    String classValue = stringOr(body.get("classValue"), null);
    if (classValue == null || classValue.isBlank()) {
      throw new StudentException("VALIDATION", "classValue is required");
    }
    Map<String, Object> settings = lifecycleSettings(scope);
    String classField = stringOr(settings.get("classFieldKey"), "classApplied");
    List<UUID> studentIds = new ArrayList<>();
    for (StudentRecordEntity student : studentsInScope(scope)) {
      if (classValue.equals(stringOr(student.getAnswers().get(classField), ""))) {
        studentIds.add(student.getId());
      }
    }
    if (studentIds.isEmpty()) {
      throw new StudentException("NOT_FOUND", "No students found in class '" + classValue + "'");
    }
    Map<String, Object> promoteBody = new LinkedHashMap<>(body);
    promoteBody.put("studentIds", studentIds.stream().map(UUID::toString).toList());
    Map<String, Object> out = promote(promoteBody);
    out.put("classValue", classValue);
    return out;
  }

  @Transactional
  public Map<String, Object> rolloverByClass(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    String classValue = stringOr(body.get("classValue"), null);
    if (classValue == null || classValue.isBlank()) {
      throw new StudentException("VALIDATION", "classValue is required");
    }
    Map<String, Object> settings = lifecycleSettings(scope);
    String classField = stringOr(settings.get("classFieldKey"), "classApplied");
    List<UUID> studentIds = new ArrayList<>();
    for (StudentRecordEntity student : studentsInScope(scope)) {
      if (classValue.equals(stringOr(student.getAnswers().get(classField), ""))) {
        studentIds.add(student.getId());
      }
    }
    if (studentIds.isEmpty()) {
      throw new StudentException("NOT_FOUND", "No students found in class '" + classValue + "'");
    }
    Map<String, Object> rolloverBody = new LinkedHashMap<>(body);
    rolloverBody.put("studentIds", studentIds.stream().map(UUID::toString).toList());
    Map<String, Object> out = rollover(rolloverBody);
    out.put("classValue", classValue);
    return out;
  }

  @Transactional
  public Map<String, Object> promote(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    ensureDefaults(scope);
    List<UUID> studentIds = parseStudentIds(body);
    if (studentIds.isEmpty()) {
      throw new StudentException("VALIDATION", "studentIds is required");
    }
    Map<String, Object> settings = lifecycleSettings(scope);
    String classField = stringOr(settings.get("classFieldKey"), "classApplied");
    String mapKey =
        stringOr(body.get("promotionMapKey"), stringOr(settings.get("defaultPromotionMapKey"), "default_grade_map"));
    Map<String, Object> mapDef = requireDefinition(scope, LifecycleCatalog.TYPE_PROMOTION_MAP, mapKey);
    Map<String, String> mappings = stringMap(mapDef.get("mappings"));
    String explicitTarget = stringOr(body.get("targetClass"), null);
    String reason = stringOr(body.get("reason"), "Annual promotion");
    Map<String, Object> statusPolicy = statusPolicy(scope, settings);

    List<Map<String, Object>> results = new ArrayList<>();
    for (UUID studentId : studentIds) {
      StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
      assertNotTerminal(student, statusPolicy);
      String fromClass = stringOr(student.getAnswers().get(classField), "");
      String toClass =
          explicitTarget != null && !explicitTarget.isBlank()
              ? explicitTarget
              : mappings.get(fromClass);
      if (toClass == null || toClass.isBlank()) {
        throw new StudentException(
            "VALIDATION",
            "No promotion mapping for class '" + fromClass + "' (student " + studentId + ")");
      }
      String fromStatus = student.getStatus();
      student.getAnswers().put(classField, toClass);
      String promotedStatus = stringOr(statusPolicy.get("promotedStatus"), "ACTIVE");
      student.setStatus(promotedStatus);
      student.setUpdatedAt(Instant.now());
      appendHistory(
          student,
          LifecycleCatalog.EVENT_PROMOTE,
          reason,
          Map.of("fromClass", fromClass, "toClass", toClass, "fromStatus", fromStatus));

      LifecycleEventEntity event =
          newEvent(
              scope,
              student,
              LifecycleCatalog.EVENT_PROMOTE,
              "COMPLETED",
              stringOr(body.get("idempotencyKey"), null),
              Map.of(
                  "fromClass", fromClass,
                  "toClass", toClass,
                  "classFieldKey", classField,
                  "promotionMapKey", mapKey,
                  "reason", reason));
      eventRepo.save(event);
      studentRepo.save(student);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("studentId", student.getId().toString());
      row.put("admissionNo", student.getAdmissionNo());
      row.put("fromClass", fromClass);
      row.put("toClass", toClass);
      row.put("status", student.getStatus());
      row.put("eventId", event.getId().toString());
      row.put("referenceNo", event.getReferenceNo());
      results.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("count", results.size());
    out.put("results", results);
    return out;
  }

  @Transactional
  public Map<String, Object> rollover(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    ensureDefaults(scope);
    List<UUID> studentIds = parseStudentIds(body);
    if (studentIds.isEmpty()) {
      throw new StudentException("VALIDATION", "studentIds is required");
    }
    Map<String, Object> settings = lifecycleSettings(scope);
    String targetSession =
        stringOr(body.get("targetSessionId"), stringOr(body.get("targetSessionKey"), null));
    if (targetSession == null || targetSession.isBlank()) {
      String currentKey =
          stringOr(scope.academicSessionId(), stringOr(settings.get("currentSessionKey"), "2025-26"));
      Map<String, Object> sessionDef =
          requireDefinition(scope, LifecycleCatalog.TYPE_ACADEMIC_SESSION, currentKey);
      targetSession = stringOr(sessionDef.get("nextSessionKey"), null);
    }
    if (targetSession == null || targetSession.isBlank()) {
      throw new StudentException("VALIDATION", "targetSessionId is required (or configure nextSessionKey)");
    }
    boolean alsoPromote = Boolean.TRUE.equals(body.get("alsoPromote"));
    String reason = stringOr(body.get("reason"), "Session rollover to " + targetSession);
    Map<String, Object> statusPolicy = statusPolicy(scope, settings);
    String classField = stringOr(settings.get("classFieldKey"), "classApplied");
    Map<String, String> mappings = Map.of();
    if (alsoPromote) {
      String mapKey =
          stringOr(
              body.get("promotionMapKey"),
              stringOr(settings.get("defaultPromotionMapKey"), "default_grade_map"));
      mappings = stringMap(requireDefinition(scope, LifecycleCatalog.TYPE_PROMOTION_MAP, mapKey).get("mappings"));
    }

    List<Map<String, Object>> results = new ArrayList<>();
    for (UUID studentId : studentIds) {
      StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
      assertNotTerminal(student, statusPolicy);
      String fromSession = student.getAcademicSessionId();
      String fromClass = stringOr(student.getAnswers().get(classField), "");
      String toClass = fromClass;
      if (alsoPromote) {
        String mapped = mappings.get(fromClass);
        if (mapped != null && !mapped.isBlank()) {
          toClass = mapped;
          student.getAnswers().put(classField, toClass);
        }
      }
      student.setAcademicSessionId(targetSession);
      student.setUpdatedAt(Instant.now());
      appendHistory(
          student,
          LifecycleCatalog.EVENT_ROLLOVER,
          reason,
          Map.of(
              "fromSession", stringOr(fromSession, ""),
              "toSession", targetSession,
              "fromClass", fromClass,
              "toClass", toClass));

      LifecycleEventEntity event =
          newEvent(
              scope,
              student,
              LifecycleCatalog.EVENT_ROLLOVER,
              "COMPLETED",
              stringOr(body.get("idempotencyKey"), null),
              Map.of(
                  "fromSession", stringOr(fromSession, ""),
                  "toSession", targetSession,
                  "fromClass", fromClass,
                  "toClass", toClass,
                  "alsoPromote", alsoPromote,
                  "reason", reason));
      eventRepo.save(event);
      studentRepo.save(student);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("studentId", student.getId().toString());
      row.put("admissionNo", student.getAdmissionNo());
      row.put("fromSession", fromSession);
      row.put("toSession", targetSession);
      row.put("fromClass", fromClass);
      row.put("toClass", toClass);
      row.put("eventId", event.getId().toString());
      results.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("count", results.size());
    out.put("targetSessionId", targetSession);
    out.put("results", results);
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> previewTcClearance(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    ensureDefaults(scope);
    UUID studentId = parseUuid(body.get("studentId"), "studentId");
    Map<String, Object> settings = lifecycleSettings(scope);
    String tcPolicyKey =
        stringOr(body.get("tcPolicyKey"), stringOr(settings.get("defaultTcPolicyKey"), "default_tc"));
    Map<String, Object> tcPolicy = requireDefinition(scope, LifecycleCatalog.TYPE_TC_POLICY, tcPolicyKey);
    StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
    return TcClearanceService.evaluate(scope, engines, student, tcPolicy, body);
  }

  @Transactional
  public Map<String, Object> issueTc(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    ensureDefaults(scope);
    UUID studentId = parseUuid(body.get("studentId"), "studentId");
    String idempotencyKey = stringOr(body.get("idempotencyKey"), null);
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing =
          eventRepo.findByOrganizationIdAndIdempotencyKey(scope.organizationId(), idempotencyKey);
      if (existing.isPresent()) {
        return toEventDto(existing.get());
      }
    }

    Map<String, Object> settings = lifecycleSettings(scope);
    Map<String, Object> statusPolicy = statusPolicy(scope, settings);
    String tcPolicyKey =
        stringOr(body.get("tcPolicyKey"), stringOr(settings.get("defaultTcPolicyKey"), "default_tc"));
    Map<String, Object> tcPolicy = requireDefinition(scope, LifecycleCatalog.TYPE_TC_POLICY, tcPolicyKey);
    StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
    if (Boolean.TRUE.equals(tcPolicy.get("blockIfTerminal"))) {
      assertNotTerminal(student, statusPolicy);
    }
    Map<String, Object> clearance = TcClearanceService.evaluate(scope, engines, student, tcPolicy, body);
    TcClearanceService.requireCleared(clearance);

    String reason = stringOr(body.get("reason"), "Transfer certificate issued");
    String leavingDate = stringOr(body.get("leavingDate"), LocalDate.now().toString());
    String remarks = stringOr(body.get("remarks"), "");
    String fromStatus = student.getStatus();
    String tcStatus = stringOr(statusPolicy.get("tcStatus"), "TRANSFERRED");
    student.setStatus(tcStatus);
    student.setUpdatedAt(Instant.now());

    String classField = stringOr(settings.get("classFieldKey"), "classApplied");
    Map<String, Object> tcMeta = new LinkedHashMap<>();
    tcMeta.put("reason", reason);
    tcMeta.put("leavingDate", leavingDate);
    tcMeta.put("remarks", remarks);
    tcMeta.put("fromStatus", fromStatus);
    tcMeta.put("toStatus", tcStatus);
    appendHistory(student, LifecycleCatalog.EVENT_TC, reason, tcMeta);

    String templateKey =
        stringOr(
            body.get("templateKey"),
            stringOr(tcPolicy.get("templateKey"), stringOr(settings.get("tcTemplateKey"), "transfer_certificate")));
    Map<String, Object> document = tryRenderTc(scope, student, templateKey, leavingDate, reason, remarks, classField);

    Map<String, Object> payload = new LinkedHashMap<>(tcMeta);
    payload.put("templateKey", templateKey);
    payload.put("document", document);
    payload.put("tcPolicyKey", tcPolicyKey);
    payload.put("clearance", clearance);

    LifecycleEventEntity event =
        newEvent(scope, student, LifecycleCatalog.EVENT_TC, "COMPLETED", idempotencyKey, payload);
    eventRepo.save(event);
    studentRepo.save(student);

    Map<String, Object> out = toEventDto(event);
    out.put("student", slimStudent(student, classField));
    out.put("hasTcDocument", document != null && "READY".equals(document.get("status")));
    return out;
  }

  @Transactional
  public Map<String, Object> markDropout(Map<String, Object> body) {
    return statusTransition(body, LifecycleCatalog.EVENT_DROPOUT, "dropoutStatus", "DROPOUT");
  }

  @Transactional
  public Map<String, Object> markAlumni(Map<String, Object> body) {
    return statusTransition(body, LifecycleCatalog.EVENT_ALUMNI, "alumniStatus", "ALUMNI");
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listEvents(UUID studentId) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    List<LifecycleEventEntity> events;
    if (studentId != null) {
      events =
          eventRepo.findByOrganizationIdAndStudentIdOrderByCreatedAtDesc(
              scope.organizationId(), studentId);
    } else {
      events = eventRepo.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId());
    }
    return events.stream().limit(100).map(this::toEventDto).toList();
  }

  private Map<String, Object> statusTransition(
      Map<String, Object> body, String eventType, String statusKey, String defaultStatus) {
    TenantScope scope = TenantContext.require();
    requireLifecycle(scope);
    ensureDefaults(scope);
    UUID studentId = parseUuid(body.get("studentId"), "studentId");
    Map<String, Object> settings = lifecycleSettings(scope);
    Map<String, Object> statusPolicy = statusPolicy(scope, settings);
    StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
    assertNotTerminal(student, statusPolicy);
    String reason = stringOr(body.get("reason"), eventType);
    String fromStatus = student.getStatus();
    String toStatus = stringOr(statusPolicy.get(statusKey), defaultStatus);
    student.setStatus(toStatus);
    student.setUpdatedAt(Instant.now());
    appendHistory(
        student,
        eventType,
        reason,
        Map.of("fromStatus", fromStatus, "toStatus", toStatus));

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("reason", reason);
    payload.put("fromStatus", fromStatus);
    payload.put("toStatus", toStatus);
    LifecycleEventEntity event =
        newEvent(
            scope,
            student,
            eventType,
            "COMPLETED",
            stringOr(body.get("idempotencyKey"), null),
            payload);
    eventRepo.save(event);
    studentRepo.save(student);
    Map<String, Object> out = toEventDto(event);
    out.put("studentId", student.getId().toString());
    out.put("status", student.getStatus());
    return out;
  }

  private Map<String, Object> tryRenderTc(
      TenantScope scope,
      StudentRecordEntity student,
      String templateKey,
      String leavingDate,
      String reason,
      String remarks,
      String classField) {
    Map<String, Object> data = new LinkedHashMap<>();
    Map<String, Object> studentData = new LinkedHashMap<>();
    studentData.put("id", student.getId().toString());
    studentData.put("name", stringOr(student.getAnswers().get("fullName"), student.getAdmissionNo()));
    studentData.put("admissionNo", student.getAdmissionNo());
    studentData.put("classSection", stringOr(student.getAnswers().get(classField), ""));
    studentData.put("status", student.getStatus());
    studentData.putAll(student.getAnswers());
    data.put("student", studentData);
    Map<String, Object> tc = new LinkedHashMap<>();
    tc.put("leavingDate", leavingDate);
    tc.put("reason", reason);
    tc.put("remarks", remarks);
    tc.put("issuedAt", Instant.now().toString());
    data.put("tc", tc);
    data.put("context", Map.of(
        "organizationId", scope.organizationId(),
        "branchId", stringOr(scope.branchId(), ""),
        "academicSessionId", stringOr(student.getAcademicSessionId(), "")));

    try {
      Map<String, Object> rendered = engines.renderReport(scope, templateKey, data);
      if (rendered == null || rendered.isEmpty()) {
        return failedDoc(templateKey, "Report render returned empty");
      }
      Object content = rendered.get("contentBase64");
      if (content == null || String.valueOf(content).isBlank()) {
        content = rendered.get("pdfBase64");
      }
      if (content == null || String.valueOf(content).isBlank()) {
        return failedDoc(templateKey, "Report render returned no PDF");
      }
      Map<String, Object> doc = new LinkedHashMap<>();
      doc.put("type", "TRANSFER_CERTIFICATE");
      doc.put("templateKey", templateKey);
      doc.put("status", "READY");
      doc.put("fileName", rendered.getOrDefault("fileName", templateKey + ".pdf"));
      doc.put("contentBase64", content);
      doc.put("renderedAt", Instant.now().toString());
      return doc;
    } catch (Exception ex) {
      return failedDoc(templateKey, ex.getMessage());
    }
  }

  private static Map<String, Object> failedDoc(String templateKey, String error) {
    Map<String, Object> failed = new LinkedHashMap<>();
    failed.put("type", "TRANSFER_CERTIFICATE");
    failed.put("templateKey", templateKey);
    failed.put("status", "FAILED");
    failed.put("error", error != null ? error : "render failed");
    failed.put("renderedAt", Instant.now().toString());
    return failed;
  }

  @Transactional
  public void ensureDefaults(TenantScope scope) {
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), LifecycleCatalog.TYPE_PROMOTION_MAP)) {
      for (Map<String, Object> m : LifecycleCatalog.defaultPromotionMaps()) {
        saveSeed(scope, LifecycleCatalog.TYPE_PROMOTION_MAP, m);
      }
    }
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), LifecycleCatalog.TYPE_STATUS_POLICY)) {
      for (Map<String, Object> m : LifecycleCatalog.defaultStatusPolicies()) {
        saveSeed(scope, LifecycleCatalog.TYPE_STATUS_POLICY, m);
      }
    }
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), LifecycleCatalog.TYPE_TC_POLICY)) {
      for (Map<String, Object> m : LifecycleCatalog.defaultTcPolicies()) {
        saveSeed(scope, LifecycleCatalog.TYPE_TC_POLICY, m);
      }
    }
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), LifecycleCatalog.TYPE_ACADEMIC_SESSION)) {
      for (Map<String, Object> m : LifecycleCatalog.defaultSessions()) {
        saveSeed(scope, LifecycleCatalog.TYPE_ACADEMIC_SESSION, m);
      }
    }
  }

  private void saveSeed(TenantScope scope, String type, Map<String, Object> payload) {
    String key = stringOr(payload.get("definitionKey"), "default");
    LifecycleDefinitionEntity entity = new LifecycleDefinitionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setDefinitionType(type);
    entity.setDefinitionKey(key);
    entity.setStatus("ACTIVE");
    entity.setVersion(1);
    entity.setPayload(new LinkedHashMap<>(payload));
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    definitionRepo.save(entity);
  }

  private LifecycleEventEntity newEvent(
      TenantScope scope,
      StudentRecordEntity student,
      String eventType,
      String status,
      String idempotencyKey,
      Map<String, Object> payload) {
    UUID id = UUID.randomUUID();
    LifecycleEventEntity event = new LifecycleEventEntity();
    event.setId(id);
    event.setOrganizationId(scope.organizationId());
    event.setBranchId(scope.branchId());
    event.setAcademicSessionId(student.getAcademicSessionId());
    event.setEventType(eventType);
    event.setStatus(status);
    event.setStudentId(student.getId());
    event.setReferenceNo(eventType.substring(0, Math.min(3, eventType.length())).toUpperCase()
        + "-"
        + id.toString().substring(0, 8).toUpperCase());
    event.setIdempotencyKey(idempotencyKey);
    event.setPayload(new LinkedHashMap<>(payload));
    event.setCreatedAt(Instant.now());
    event.setUpdatedAt(Instant.now());
    return event;
  }

  private void appendHistory(
      StudentRecordEntity student, String type, String message, Map<String, Object> extra) {
    List<Map<String, Object>> history = student.getHistory();
    if (history == null) {
      history = new ArrayList<>();
      student.setHistory(history);
    }
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", type);
    event.put("at", Instant.now().toString());
    event.put("userId", TenantContext.require().userId());
    event.put("role", TenantContext.require().roleCode());
    event.put("message", message);
    if (extra != null) {
      event.putAll(extra);
    }
    history.add(event);
  }

  private List<StudentRecordEntity> studentsInScope(TenantScope scope) {
    if (scope.branchId() != null && !scope.branchId().isBlank()
        && scope.academicSessionId() != null && !scope.academicSessionId().isBlank()) {
      return studentRepo.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId());
    }
    return studentRepo.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId());
  }

  private void requireLifecycle(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, StudentRecordService.FEATURE_STUDENT_MASTER)) {
      throw new StudentException(
          "FEATURE_OFF", "FEATURE_STUDENT_MASTER is off for this subscription plan.");
    }
    if (!engines.isFeatureEnabled(scope, LifecycleCatalog.FEATURE_ACADEMIC_LIFECYCLE)) {
      throw new StudentException(
          "FEATURE_OFF", "FEATURE_ACADEMIC_LIFECYCLE is off for this subscription plan.");
    }
  }

  private Map<String, Object> lifecycleSettings(TenantScope scope) {
    return moduleSettingsMap(engines.getModuleSettings(scope, StudentRecordService.MODULE_STUDENT));
  }

  private Map<String, Object> statusPolicy(TenantScope scope, Map<String, Object> settings) {
    String key =
        stringOr(settings.get("defaultStatusPolicyKey"), "default_statuses");
    return requireDefinition(scope, LifecycleCatalog.TYPE_STATUS_POLICY, key);
  }

  private void assertNotTerminal(StudentRecordEntity student, Map<String, Object> statusPolicy) {
    Object raw = statusPolicy.get("terminalStatuses");
    if (!(raw instanceof List<?> list)) {
      return;
    }
    for (Object item : list) {
      if (String.valueOf(item).equalsIgnoreCase(student.getStatus())) {
        throw new StudentException(
            "VALIDATION",
            "Student is in terminal status " + student.getStatus() + " and cannot be changed");
      }
    }
  }

  private Map<String, Object> requireDefinition(TenantScope scope, String type, String key) {
    LifecycleDefinitionEntity entity =
        definitionRepo
            .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                scope.organizationId(), type, key, "ACTIVE")
            .orElseThrow(
                () ->
                    new StudentException(
                        "NOT_FOUND", "Lifecycle definition not found: " + type + "/" + key));
    return entity.getPayload() != null ? entity.getPayload() : Map.of();
  }

  private StudentRecordEntity requireStudent(UUID id, String orgId) {
    return studentRepo
        .findByIdAndOrganizationId(id, orgId)
        .orElseThrow(() -> new StudentException("NOT_FOUND", "Student not found"));
  }

  private Map<String, Object> toDefinitionDto(LifecycleDefinitionEntity e) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", e.getId());
    dto.put("definitionType", e.getDefinitionType());
    dto.put("definitionKey", e.getDefinitionKey());
    dto.put("status", e.getStatus());
    dto.put("version", e.getVersion());
    dto.put("payload", e.getPayload());
    dto.put("branchId", e.getBranchId());
    dto.put("academicSessionId", e.getAcademicSessionId());
    dto.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
    dto.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
    if (e.getPayload() != null) {
      e.getPayload().forEach((k, v) -> {
        if (!dto.containsKey(k)) {
          dto.put(k, v);
        }
      });
    }
    return dto;
  }

  private Map<String, Object> toEventDto(LifecycleEventEntity e) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", e.getId().toString());
    dto.put("eventType", e.getEventType());
    dto.put("status", e.getStatus());
    dto.put("studentId", e.getStudentId().toString());
    dto.put("referenceNo", e.getReferenceNo());
    dto.put("academicSessionId", e.getAcademicSessionId());
    dto.put("payload", e.getPayload());
    dto.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
    return dto;
  }

  private Map<String, Object> slimStudent(StudentRecordEntity s, String classField) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId().toString());
    m.put("admissionNo", s.getAdmissionNo());
    m.put("status", s.getStatus());
    m.put("academicSessionId", s.getAcademicSessionId());
    m.put("fullName", s.getAnswers().get("fullName"));
    m.put("classApplied", s.getAnswers().get(classField));
    return m;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> moduleSettingsMap(Map<String, Object> module) {
    if (module == null) {
      return Map.of();
    }
    Object settings = module.get("settings");
    if (settings instanceof Map<?, ?> m) {
      return new LinkedHashMap<>((Map<String, Object>) m);
    }
    return new LinkedHashMap<>(module);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> stringMap(Object raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw instanceof Map<?, ?> m) {
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (e.getKey() != null && e.getValue() != null) {
          out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
      }
    }
    return out;
  }

  private static List<UUID> parseStudentIds(Map<String, Object> body) {
    List<UUID> ids = new ArrayList<>();
    Object raw = body.get("studentIds");
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        ids.add(parseUuid(item, "studentIds"));
      }
    } else if (body.get("studentId") != null) {
      ids.add(parseUuid(body.get("studentId"), "studentId"));
    }
    return ids;
  }

  private static UUID parseUuid(Object raw, String field) {
    if (raw == null) {
      throw new StudentException("VALIDATION", field + " is required");
    }
    try {
      return UUID.fromString(String.valueOf(raw));
    } catch (IllegalArgumentException ex) {
      throw new StudentException("VALIDATION", field + " must be a UUID");
    }
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? fallback : s;
  }
}
