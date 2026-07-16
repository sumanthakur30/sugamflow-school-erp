package com.sugamflow.school.student.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.student.config.StudentProperties;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
public class StudentRecordService {

  public static final String FEATURE_STUDENT_MASTER = "FEATURE_STUDENT_MASTER";
  public static final String MODULE_STUDENT = "student";

  private final StudentRecordRepository repository;
  private final ConfigEngineClient engines;
  private final StudentProperties properties;

  public StudentRecordService(
      StudentRecordRepository repository, ConfigEngineClient engines, StudentProperties properties) {
    this.repository = repository;
    this.engines = engines;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STUDENT);
    Map<String, Object> settings = moduleSettingsMap(module);
    String formKey = resolveFormKey(module);
    Map<String, Object> form = engines.getForm(scope, formKey);
    if (form == null) {
      throw new StudentException("FORM_MISSING", "Form definition not found: " + formKey);
    }
    String parentFormKey = stringOr(settings.get("parentFormKey"), "parent_master");
    Map<String, Object> parentForm = engines.getForm(scope, parentFormKey);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("module", module);
    out.put("formKey", formKey);
    out.put("form", form);
    out.put("parentFormKey", parentFormKey);
    out.put("parentForm", parentForm);
    out.put("guardiansAnswerKey", stringOr(settings.get("guardiansAnswerKey"), "guardians"));
    out.put("maxGuardians", settings.getOrDefault("maxGuardians", 4));
    return out;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    PageQuery q = PageQuery.of(page, size);
    Pageable pageable = PageRequest.of(q.page(), q.size());
    Page<StudentRecordEntity> result;
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
    return toDto(requireStudent(id, scope.organizationId()));
  }

  /**
   * Idempotent enrollment from an approved admission application. Field mapping is driven by the
   * target form keys (+ optional admissionFieldMap from the request body / module settings).
   */
  @Transactional
  public Map<String, Object> enrollFromAdmission(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requireModuleEnabled(scope);

    UUID applicationId = parseUuid(body.get("applicationId"), "applicationId");
    var existing =
        repository.findByOrganizationIdAndSourceApplicationId(scope.organizationId(), applicationId);
    if (existing.isPresent()) {
      return toDto(existing.get());
    }

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STUDENT);
    Map<String, Object> admissionModule = engines.getModuleSettings(scope, "admission");
    Map<String, Object> admissionSettings = moduleSettingsMap(admissionModule);

    String formKey =
        stringOr(body.get("formKey"), stringOr(admissionSettings.get("studentFormKey"), resolveFormKey(module)));
    Map<String, Object> form = engines.getForm(scope, formKey);
    if (form == null) {
      throw new StudentException("FORM_MISSING", "Form definition not found: " + formKey);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> sourceAnswers =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    Map<String, Object> fieldMap =
        body.get("fieldMap") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : (admissionSettings.get("admissionFieldMap") instanceof Map<?, ?> am
                ? new LinkedHashMap<>((Map<String, Object>) am)
                : Map.of());

    Map<String, Object> answers = mapAnswers(form, sourceAnswers, fieldMap);
    boolean generateNo =
        admissionSettings.get("generateAdmissionNo") == null
            || Boolean.TRUE.equals(admissionSettings.get("generateAdmissionNo"));
    String admissionNo =
        stringOr(
            answers.get("admissionNo"),
            generateNo ? generateAdmissionNo(scope) : null);
    if (admissionNo != null && !admissionNo.isBlank()) {
      answers.put("admissionNo", admissionNo);
    }

    attachGuardiansOnEnroll(scope, module, answers, sourceAnswers, body);

    validateMandatory(form, answers);

    StudentRecordEntity entity = new StudentRecordEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setFormKey(formKey);
    entity.setStatus("ACTIVE");
    entity.setAdmissionNo(admissionNo);
    entity.setSourceApplicationId(applicationId);
    entity.setAnswers(answers);
    entity.setCreatedBy(scope.userId());
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    List<Map<String, Object>> history = new ArrayList<>();
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "ENROLLED_FROM_ADMISSION");
    event.put("at", Instant.now().toString());
    event.put("userId", scope.userId());
    event.put("role", scope.roleCode());
    event.put("applicationId", applicationId.toString());
    event.put("message", "Enrolled from approved admission application");
    history.add(event);
    entity.setHistory(history);

    return toDto(repository.save(entity));
  }

  /**
   * Replace guardians list on a student. Body: { "guardians": [ {...} ] } validated against
   * parentFormKey from module settings.
   */
  @Transactional
  public Map<String, Object> replaceGuardians(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    StudentRecordEntity entity = requireStudent(id, scope.organizationId());
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STUDENT);
    Map<String, Object> settings = moduleSettingsMap(module);
    String guardiansKey = stringOr(settings.get("guardiansAnswerKey"), "guardians");
    int max = intOr(settings.get("maxGuardians"), 4);

    List<Map<String, Object>> guardians = extractGuardiansList(body.get("guardians"));
    if (guardians == null) {
      guardians = extractGuardiansList(body.get(guardiansKey));
    }
    if (guardians == null) {
      throw new StudentException("VALIDATION", "guardians array is required");
    }
    if (guardians.size() > max) {
      throw new StudentException("VALIDATION", "At most " + max + " guardians allowed");
    }

    String parentFormKey = stringOr(settings.get("parentFormKey"), "parent_master");
    Map<String, Object> parentForm = engines.getForm(scope, parentFormKey);
    if (parentForm == null) {
      throw new StudentException("FORM_MISSING", "Parent form not found: " + parentFormKey);
    }
    List<Map<String, Object>> normalized = new ArrayList<>();
    for (Map<String, Object> g : guardians) {
      Map<String, Object> row = normalizeGuardian(parentForm, g);
      validateMandatory(parentForm, row);
      normalized.add(row);
    }

    Map<String, Object> answers = new LinkedHashMap<>(entity.getAnswers());
    answers.put(guardiansKey, normalized);
    entity.setAnswers(answers);
    entity.setUpdatedAt(Instant.now());

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "GUARDIANS_UPDATED");
    event.put("at", Instant.now().toString());
    event.put("userId", scope.userId());
    event.put("role", scope.roleCode());
    event.put("message", "Guardians updated (" + normalized.size() + ")");
    entity.getHistory().add(event);

    return toDto(repository.save(entity));
  }

  @SuppressWarnings("unchecked")
  private void attachGuardiansOnEnroll(
      TenantScope scope,
      Map<String, Object> studentModule,
      Map<String, Object> answers,
      Map<String, Object> sourceAnswers,
      Map<String, Object> body) {
    Map<String, Object> settings = moduleSettingsMap(studentModule);
    if (Boolean.FALSE.equals(settings.get("captureGuardiansOnEnroll"))) {
      return;
    }
    String guardiansKey = stringOr(settings.get("guardiansAnswerKey"), "guardians");
    int max = intOr(settings.get("maxGuardians"), 4);

    List<Map<String, Object>> guardians = extractGuardiansList(body.get("guardians"));
    if (guardians == null) {
      guardians = extractGuardiansList(sourceAnswers.get(guardiansKey));
    }
    if (guardians == null || guardians.isEmpty()) {
      guardians = buildGuardiansFromFlatMap(sourceAnswers, settings);
    }
    if (guardians == null || guardians.isEmpty()) {
      return;
    }
    if (guardians.size() > max) {
      guardians = new ArrayList<>(guardians.subList(0, max));
    }

    String parentFormKey = stringOr(settings.get("parentFormKey"), "parent_master");
    Map<String, Object> parentForm = engines.getForm(scope, parentFormKey);
    List<Map<String, Object>> normalized = new ArrayList<>();
    for (Map<String, Object> g : guardians) {
      Map<String, Object> row =
          parentForm != null ? normalizeGuardian(parentForm, g) : new LinkedHashMap<>(g);
      if (parentForm != null) {
        // Soft validation on enroll: skip incomplete guardians rather than fail enrollment
        try {
          validateMandatory(parentForm, row);
          normalized.add(row);
        } catch (StudentException ex) {
          if (hasAnyValue(row)) {
            normalized.add(row);
          }
        }
      } else if (hasAnyValue(row)) {
        normalized.add(row);
      }
    }
    if (!normalized.isEmpty()) {
      answers.put(guardiansKey, normalized);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> buildGuardiansFromFlatMap(
      Map<String, Object> source, Map<String, Object> settings) {
    Map<String, Object> fieldMap =
        settings.get("guardianFieldMap") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : Map.of(
                "guardianFullName", "fullName",
                "guardianRelation", "relation",
                "guardianMobile", "mobile",
                "guardianEmail", "email");
    Map<String, Object> guardian = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : fieldMap.entrySet()) {
      String admissionKey = e.getKey();
      String parentKey = String.valueOf(e.getValue());
      if (source.containsKey(admissionKey) && source.get(admissionKey) != null) {
        String v = String.valueOf(source.get(admissionKey)).trim();
        if (!v.isEmpty()) {
          guardian.put(parentKey, source.get(admissionKey));
        }
      }
    }
    if (guardian.isEmpty()) {
      return List.of();
    }
    if (!guardian.containsKey("isPrimary")) {
      guardian.put("isPrimary", true);
    }
    return List.of(guardian);
  }

  private Map<String, Object> normalizeGuardian(Map<String, Object> parentForm, Map<String, Object> raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (String key : formFieldKeys(parentForm)) {
      if (raw.containsKey(key)) {
        Object v = raw.get(key);
        if ("isPrimary".equals(key) || "CHECKBOX".equals(fieldType(parentForm, key))) {
          out.put(key, Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v)));
        } else {
          out.put(key, v);
        }
      }
    }
    return out;
  }

  private String fieldType(Map<String, Object> form, String key) {
    Object sectionsObj = form.get("sections");
    if (!(sectionsObj instanceof List<?> sections)) {
      return "TEXTBOX";
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
        if (fieldObj instanceof Map<?, ?> field && key.equals(String.valueOf(field.get("key")))) {
          Object type = field.get("type");
          return type == null ? "TEXTBOX" : String.valueOf(type);
        }
      }
    }
    return "TEXTBOX";
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> extractGuardiansList(Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return null;
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m) {
        out.add(new LinkedHashMap<>((Map<String, Object>) m));
      }
    }
    return out.isEmpty() ? null : out;
  }

  private static boolean hasAnyValue(Map<String, Object> row) {
    for (Object v : row.values()) {
      if (v == null) {
        continue;
      }
      if (v instanceof Boolean) {
        return true;
      }
      if (!String.valueOf(v).isBlank()) {
        return true;
      }
    }
    return false;
  }

  private static int intOr(Object v, int d) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return v != null ? Integer.parseInt(String.valueOf(v)) : d;
    } catch (NumberFormatException ex) {
      return d;
    }
  }

  private Map<String, Object> mapAnswers(
      Map<String, Object> form,
      Map<String, Object> source,
      Map<String, Object> fieldMap) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<String> targets = formFieldKeys(form);
    for (String key : targets) {
      if (source.containsKey(key)) {
        out.put(key, source.get(key));
      }
    }
    // admissionFieldMap: admissionKey -> studentKey
    for (Map.Entry<String, Object> e : fieldMap.entrySet()) {
      String admissionKey = e.getKey();
      String studentKey = String.valueOf(e.getValue());
      if (targets.contains(studentKey) && source.containsKey(admissionKey)) {
        out.put(studentKey, source.get(admissionKey));
      }
    }
    return out;
  }

  private List<String> formFieldKeys(Map<String, Object> form) {
    List<String> keys = new ArrayList<>();
    Object sectionsObj = form.get("sections");
    if (!(sectionsObj instanceof List<?> sections)) {
      return keys;
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
        if (fieldObj instanceof Map<?, ?> field && field.get("key") != null) {
          keys.add(String.valueOf(field.get("key")));
        }
      }
    }
    return keys;
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
          throw new StudentException(
              "VALIDATION", "Mandatory field missing: " + (label != null ? label : key));
        }
      }
    }
  }

  private String generateAdmissionNo(TenantScope scope) {
    String year = String.valueOf(LocalDate.now().getYear());
    String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    String branch =
        scope.branchId() != null && !scope.branchId().isBlank()
            ? scope.branchId().toUpperCase().replaceAll("[^A-Z0-9]", "")
            : "MAIN";
    if (branch.length() > 6) {
      branch = branch.substring(0, 6);
    }
    return "ADM-" + year + "-" + branch + "-" + suffix;
  }

  private void requireFeature(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FEATURE_STUDENT_MASTER)) {
      throw new StudentException(
          "FEATURE_DISABLED", "FEATURE_STUDENT_MASTER is off for this subscription plan.");
    }
  }

  private void requireModuleEnabled(TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STUDENT);
    Map<String, Object> settings = moduleSettingsMap(module);
    Object enabled = settings.get("enabled");
    if (enabled != null && Boolean.FALSE.equals(enabled)) {
      throw new StudentException("MODULE_DISABLED", "Student module is disabled in module settings.");
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

  private StudentRecordEntity requireStudent(UUID id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new StudentException("NOT_FOUND", "Student not found"));
  }

  private static UUID parseUuid(Object raw, String field) {
    if (raw == null || String.valueOf(raw).isBlank()) {
      throw new StudentException("VALIDATION", field + " is required");
    }
    try {
      return UUID.fromString(String.valueOf(raw));
    } catch (IllegalArgumentException ex) {
      throw new StudentException("VALIDATION", field + " must be a UUID");
    }
  }

  private Map<String, Object> toDto(StudentRecordEntity e) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", e.getId().toString());
    dto.put("organizationId", e.getOrganizationId());
    dto.put("branchId", e.getBranchId());
    dto.put("academicSessionId", e.getAcademicSessionId());
    dto.put("formKey", e.getFormKey());
    dto.put("status", e.getStatus());
    dto.put("admissionNo", e.getAdmissionNo());
    dto.put(
        "sourceApplicationId",
        e.getSourceApplicationId() != null ? e.getSourceApplicationId().toString() : null);
    dto.put("answers", e.getAnswers());
    Object guardians = e.getAnswers() != null ? e.getAnswers().get("guardians") : null;
    dto.put("guardians", guardians instanceof List<?> ? guardians : List.of());
    dto.put("guardianCount", guardians instanceof List<?> list ? list.size() : 0);
    dto.put("history", e.getHistory());
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
}
