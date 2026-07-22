package com.sugamflow.school.student.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.api.PageResults;
import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.student.config.StudentProperties;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentFieldAuditEntity;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentFieldAuditRepository;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.integration.DomainSnapshotClient;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentRecordService {

  public static final String FEATURE_STUDENT_MASTER = "FEATURE_STUDENT_MASTER";
  public static final String MODULE_STUDENT = "student";
  public static final String STATUS_DELETED = "DELETED";

  private static final Pattern MOBILE_RE = Pattern.compile("^[0-9+\\-\\s]{7,20}$");
  private static final Pattern EMAIL_RE =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  private static final Pattern PIN_RE = Pattern.compile("^[0-9]{4,10}$");
  private static final Set<String> ALLOWED_STATUSES =
      Set.of(
          "ACTIVE",
          "INACTIVE",
          "LEFT_SCHOOL",
          "TRANSFERRED",
          "TC_ISSUED",
          "ALUMNI",
          "SUSPENDED",
          "EXPELLED",
          "PASSED_OUT",
          "PROMOTED",
          "DROPOUT",
          STATUS_DELETED);

  private final StudentRecordRepository repository;
  private final StudentFieldAuditRepository fieldAuditRepository;
  private final ConfigEngineClient engines;
  private final StudentProperties properties;
  private final RelationshipAccessService relationshipAccess;
  private final DomainSnapshotClient domainSnapshots;

  public StudentRecordService(
      StudentRecordRepository repository,
      StudentFieldAuditRepository fieldAuditRepository,
      ConfigEngineClient engines,
      StudentProperties properties,
      RelationshipAccessService relationshipAccess,
      DomainSnapshotClient domainSnapshots) {
    this.repository = repository;
    this.fieldAuditRepository = fieldAuditRepository;
    this.engines = engines;
    this.properties = properties;
    this.relationshipAccess = relationshipAccess;
    this.domainSnapshots = domainSnapshots;
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
  public AccessScope accessScope() {
    return relationshipAccess.resolve(TenantContext.require());
  }

  /**
   * Deduplicated guardian delivery targets for staff outreach (Comms Hub, fee reminders, etc.).
   * Prefer {@code authUsername}/{@code username}/{@code userId} for IN_APP, plus email/mobile.
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> guardianDeliveryTargets() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requireStaffWrite(scope);

    Map<String, Map<String, Object>> byIdentity = new LinkedHashMap<>();
    Map<String, Map<String, Object>> byContactOnly = new LinkedHashMap<>();
    for (StudentRecordEntity student : loadCandidates(scope)) {
      Object raw = student.getAnswers() == null ? null : student.getAnswers().get("guardians");
      if (!(raw instanceof List<?> list)) {
        continue;
      }
      for (Object item : list) {
        if (!(item instanceof Map<?, ?> g)) {
          continue;
        }
        String identityRaw =
            firstNonBlank(
                stringOr(g.get("authUsername"), null),
                stringOr(g.get("username"), null),
                stringOr(g.get("userId"), null));
        final String identity =
            identityRaw == null || identityRaw.isBlank() ? null : identityRaw.trim();
        final String email = stringOr(g.get("email"), null);
        final String mobile = stringOr(g.get("mobile"), null);
        String fullNameRaw =
            firstNonBlank(
                stringOr(g.get("fullName"), null),
                stringOr(g.get("name"), null),
                "Guardian");
        final String fullName =
            fullNameRaw == null || fullNameRaw.isBlank() ? "Guardian" : fullNameRaw.trim();
        if (identity == null && email == null && mobile == null) {
          continue;
        }
        String key = identity != null ? "id:" + identity.toLowerCase(Locale.ROOT)
            : "contact:" + (email != null ? email.toLowerCase(Locale.ROOT) : "")
                + "|" + (mobile != null ? mobile : "");
        Map<String, Map<String, Object>> targetMap = identity != null ? byIdentity : byContactOnly;
        Map<String, Object> row = targetMap.computeIfAbsent(key, ignored -> {
          Map<String, Object> created = new LinkedHashMap<>();
          created.put("identity", identity);
          created.put("fullName", fullName);
          created.put("email", email);
          created.put("mobile", mobile);
          created.put("studentIds", new ArrayList<String>());
          return created;
        });
        if (email != null && !hasText(row.get("email"))) {
          row.put("email", email);
        }
        if (mobile != null && !hasText(row.get("mobile"))) {
          row.put("mobile", mobile);
        }
        @SuppressWarnings("unchecked")
        List<String> studentIds = (List<String>) row.get("studentIds");
        if (student.getId() != null && !studentIds.contains(student.getId().toString())) {
          studentIds.add(student.getId().toString());
        }
      }
    }
    List<Map<String, Object>> out = new ArrayList<>();
    out.addAll(byIdentity.values());
    out.addAll(byContactOnly.values());
    return out;
  }

  private static boolean hasText(Object value) {
    return value != null && !String.valueOf(value).trim().isEmpty();
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    PageQuery q = PageQuery.of(page, size);
    AccessScope access = relationshipAccess.resolve(scope);

    if (access.restricted()) {
      List<StudentRecordEntity> all = loadCandidates(scope);
      List<Map<String, Object>> dtos = new ArrayList<>();
      for (StudentRecordEntity e : all) {
        Map<String, Object> dto = toDto(e);
        if (access.allowsStudentDto(dto)) {
          dtos.add(dto);
        }
      }
      return PageResults.filterThenPage(dtos, d -> true, q.page(), q.size());
    }

    Pageable pageable = PageRequest.of(q.page(), q.size());
    Page<StudentRecordEntity> result;
    if (scope.branchId() != null && !scope.branchId().isBlank()
        && scope.academicSessionId() != null && !scope.academicSessionId().isBlank()) {
      result = repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId(), pageable);
    } else {
      result = repository.findByOrganizationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(scope.organizationId(), pageable);
    }
    return PageResult.of(result.map(this::toDto).getContent(), q.page(), q.size(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    Map<String, Object> dto = toDto(requireStudent(id, scope.organizationId()));
    AccessScope access = relationshipAccess.resolve(scope);
    if (!access.allowsStudentDto(dto)) {
      throw new StudentException("NOT_FOUND", "Student not found");
    }
    return dto;
  }

  /** Lookup by admission number (case-insensitive). Used by Fee / Attendance / Exams. */
  @Transactional(readOnly = true)
  public Map<String, Object> getByAdmissionNo(String admissionNo) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    if (admissionNo == null || admissionNo.isBlank()) {
      throw new StudentException("VALIDATION", "admissionNo is required");
    }
    StudentRecordEntity entity =
        repository
            .findByOrganizationIdAndAdmissionNoIgnoreCaseAndDeletedAtIsNull(
                scope.organizationId(), admissionNo.trim())
            .orElseThrow(() -> new StudentException("NOT_FOUND", "Student not found"));
    Map<String, Object> dto = toDto(entity);
    AccessScope access = relationshipAccess.resolve(scope);
    if (!access.allowsStudentDto(dto)) {
      throw new StudentException("NOT_FOUND", "Student not found");
    }
    return dto;
  }

  /**
   * Compact identity card payload for cross-module UIs (fee collection detail, attendance, etc.).
   * Reads live Student Master fields — not a copy stored on the fee record.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> identitySummaryByAdmissionNo(String admissionNo) {
    return toIdentitySummary(getByAdmissionNo(admissionNo));
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> toIdentitySummary(Map<String, Object> studentDto) {
    Map<String, Object> answers =
        studentDto.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : Map.of();
    String classSection =
        firstNonBlank(
            stringVal(answers, "classSection"),
            stringVal(answers, "classApplied"),
            "");
    String[] classParts = splitClassSection(classSection);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", studentDto.get("id"));
    out.put("admissionNo", studentDto.get("admissionNo"));
    out.put(
        "fullName",
        firstNonBlank(
            stringVal(answers, "fullName"),
            stringVal(answers, "studentName"),
            ""));
    out.put("rollNo", stringVal(answers, "rollNo"));
    out.put("classSection", classSection);
    out.put("className", classParts[0]);
    out.put("section", classParts[1]);
    out.put("fatherName", fatherName(answers));
    out.put("motherName", firstNonBlank(stringVal(answers, "motherName"), ""));
    out.put("guardianName", guardianName(answers));
    out.put("mobile", stringVal(answers, "mobile"));
    out.put("email", stringVal(answers, "email"));
    out.put(
        "photoUrl",
        firstNonBlank(
            stringVal(answers, "photoUrl"),
            stringVal(answers, "photo"),
            stringVal(answers, "studentPhoto"),
            ""));
    out.put("status", studentDto.get("status"));
    out.put("branchId", studentDto.get("branchId"));
    out.put("academicSessionId", studentDto.get("academicSessionId"));
    out.put("gender", stringVal(answers, "gender"));
    out.put("house", stringVal(answers, "house"));
    return out;
  }

  private static String fatherName(Map<String, Object> answers) {
    String direct = firstNonBlank(stringVal(answers, "fatherName"), stringVal(answers, "parentName"));
    if (!direct.isEmpty()) {
      return direct;
    }
    Object guardians = answers.get("guardians");
    if (guardians instanceof List<?> list) {
      for (Object g : list) {
        if (!(g instanceof Map<?, ?> m)) {
          continue;
        }
        Object rel = m.get("relation");
        String relation = rel == null ? "" : String.valueOf(rel).toLowerCase();
        if (relation.contains("father") || relation.contains("parent")) {
          return firstNonBlank(mapStr(m, "fullName"), mapStr(m, "name"));
        }
      }
    }
    return "";
  }

  private static String guardianName(Map<String, Object> answers) {
    String direct = stringVal(answers, "guardianName");
    if (!direct.isEmpty()) {
      return direct;
    }
    Object guardians = answers.get("guardians");
    if (guardians instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
      return firstNonBlank(mapStr(m, "fullName"), mapStr(m, "name"));
    }
    return "";
  }

  private static String mapStr(Map<?, ?> m, String key) {
    Object v = m.get(key);
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static String[] splitClassSection(String classSection) {
    if (classSection == null || classSection.isBlank()) {
      return new String[] {"", ""};
    }
    String s = classSection.trim();
    // "VIII-A", "Grade 8-A", "8 A"
    int dash = Math.max(s.lastIndexOf('-'), s.lastIndexOf('–'));
    if (dash > 0 && dash < s.length() - 1) {
      return new String[] {s.substring(0, dash).trim(), s.substring(dash + 1).trim()};
    }
    int space = s.lastIndexOf(' ');
    if (space > 0 && s.length() - space <= 3) {
      return new String[] {s.substring(0, space).trim(), s.substring(space + 1).trim()};
    }
    return new String[] {s, ""};
  }

  private static String stringVal(Map<String, Object> answers, String key) {
    Object v = answers.get(key);
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static String firstNonBlank(String... vals) {
    if (vals == null) {
      return "";
    }
    for (String v : vals) {
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return "";
  }

  /**
   * Idempotent enrollment from an approved admission application. Field mapping is driven by the
   * target form keys (+ optional admissionFieldMap from the request body / module settings).
   */
  @Transactional
  public Map<String, Object> enrollFromAdmission(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
    requireFeature(scope);
    requireModuleEnabled(scope);

    UUID applicationId = parseUuid(body.get("applicationId"), "applicationId");
    var existing =
        repository.findByOrganizationIdAndSourceApplicationIdAndDeletedAtIsNull(scope.organizationId(), applicationId);
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
   * Create (or upsert by admissionNo) a student from Import Workbench. Body: answers map matching
   * student_master form keys. Idempotent when admissionNo already exists for the org.
   */
  @Transactional
  public Map<String, Object> createFromImport(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
    requireFeature(scope);
    requireModuleEnabled(scope);

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STUDENT);
    String formKey = stringOr(body.get("formKey"), resolveFormKey(module));
    Map<String, Object> form = engines.getForm(scope, formKey);
    if (form == null) {
      throw new StudentException("FORM_MISSING", "Form definition not found: " + formKey);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> sourceAnswers =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>(body);

    Map<String, Object> answers = mapAnswers(form, sourceAnswers, Map.of());
    String admissionNo = stringOr(answers.get("admissionNo"), null);
    if (admissionNo != null && !admissionNo.isBlank()) {
      var existingAdm =
          repository.findByOrganizationIdAndAdmissionNoIgnoreCaseAndDeletedAtIsNull(
              scope.organizationId(), admissionNo);
      if (existingAdm.isPresent()) {
        return toDto(existingAdm.get());
      }
    } else {
      admissionNo = generateAdmissionNo(scope);
      answers.put("admissionNo", admissionNo);
    }

    if (body.get("guardians") instanceof List<?>) {
      attachGuardiansOnEnroll(scope, module, answers, sourceAnswers, body);
    }

    validateMandatory(form, answers);

    StudentRecordEntity entity = new StudentRecordEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setFormKey(formKey);
    entity.setStatus(stringOr(body.get("status"), "ACTIVE"));
    entity.setAdmissionNo(admissionNo);
    entity.setAnswers(answers);
    entity.setCreatedBy(scope.userId());
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    List<Map<String, Object>> history = new ArrayList<>();
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "IMPORTED");
    event.put("at", Instant.now().toString());
    event.put("userId", scope.userId());
    event.put("role", scope.roleCode());
    event.put("message", "Created from Import Workbench");
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
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
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

  /**
   * Update student master answers (staff-style). Records field-level audit + history event.
   * Body: { answers, reason?, status?, ipAddress?, userAgent? }
   */
  @Transactional
  public Map<String, Object> update(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireStaffWrite(scope);
    requireFeature(scope);
    StudentRecordEntity entity = requireStudent(id, scope.organizationId());
    assertNotSoftDeleted(entity);

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STUDENT);
    String formKey = stringOr(entity.getFormKey(), resolveFormKey(module));
    Map<String, Object> form = engines.getForm(scope, formKey);

    @SuppressWarnings("unchecked")
    Map<String, Object> incoming =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();

    Map<String, Object> before = new LinkedHashMap<>(entity.getAnswers());
    String beforeAdmission = entity.getAdmissionNo();
    String beforeStatus = entity.getStatus();

    // Protect admission number for non-elevated roles
    if (incoming.containsKey("admissionNo") && !PersonaRoles.isElevated(scope.roleCode())) {
      Object nextAdm = incoming.get("admissionNo");
      if (!Objects.equals(stringOr(nextAdm, ""), stringOr(beforeAdmission, ""))) {
        throw new StudentException("FORBIDDEN", "Only elevated roles can change admission number");
      }
    }

    Map<String, Object> answers = new LinkedHashMap<>(before);
    // Do not overwrite guardians via this endpoint (use replaceGuardians)
    Object preservedGuardians = before.get("guardians");
    answers.putAll(incoming);
    if (preservedGuardians != null) {
      answers.put("guardians", preservedGuardians);
    }

    if (form != null) {
      validateMandatory(form, answers);
    }
    validateFormats(answers);

    String admissionNo = stringOr(answers.get("admissionNo"), entity.getAdmissionNo());
    answers.put("admissionNo", admissionNo);
    assertUniqueAdmissionNo(scope.organizationId(), admissionNo, entity.getId());
    assertUniqueAadhaar(scope.organizationId(), answers, entity.getId());
    assertUniqueRollInClass(scope.organizationId(), answers, entity.getId());

    String reason = stringOr(body.get("reason"), "Student profile correction");
    List<Map<String, Object>> changes = diffAnswers(before, answers);
    if (!Objects.equals(stringOr(beforeAdmission, ""), stringOr(admissionNo, ""))) {
      changes.add(changeRow("admissionNo", beforeAdmission, admissionNo));
    }

    if (body.get("status") != null && !String.valueOf(body.get("status")).isBlank()) {
      String nextStatus = String.valueOf(body.get("status")).trim().toUpperCase(Locale.ROOT);
      assertAllowedStatus(nextStatus);
      if (!nextStatus.equalsIgnoreCase(beforeStatus)) {
        if (!PersonaRoles.isElevated(scope.roleCode()) && STATUS_DELETED.equals(nextStatus)) {
          throw new StudentException("FORBIDDEN", "Use soft-delete endpoint to delete a student");
        }
        entity.setStatus(nextStatus);
        changes.add(changeRow("status", beforeStatus, nextStatus));
      }
    }

    entity.setAnswers(answers);
    entity.setAdmissionNo(admissionNo);
    entity.setUpdatedAt(Instant.now());

    String ip = stringOr(body.get("ipAddress"), null);
    String ua = stringOr(body.get("userAgent"), null);
    persistFieldAudits(scope, entity.getId(), changes, reason, ip, ua, "FIELD_CHANGE");

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "UPDATED");
    event.put("at", Instant.now().toString());
    event.put("userId", scope.userId());
    event.put("role", scope.roleCode());
    event.put("message", reason);
    event.put("changes", changes);
    entity.getHistory().add(event);

    return toDto(repository.save(entity));
  }

  /** Soft delete — hides from normal lists. Body: { reason } required. */
  @Transactional
  public Map<String, Object> softDelete(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireStaffWrite(scope);
    requireFeature(scope);
    StudentRecordEntity entity = requireStudent(id, scope.organizationId());
    assertNotSoftDeleted(entity);

    String reason = stringOr(body.get("reason"), null);
    if (reason == null || reason.isBlank()) {
      throw new StudentException("VALIDATION", "Deletion reason is required");
    }

    String beforeStatus = entity.getStatus();
    Instant now = Instant.now();
    entity.setDeletedAt(now);
    entity.setDeletedBy(scope.userId());
    entity.setDeleteReason(reason);
    entity.setStatus(STATUS_DELETED);
    entity.setUpdatedAt(now);

    List<Map<String, Object>> changes = List.of(changeRow("status", beforeStatus, STATUS_DELETED));
    persistFieldAudits(
        scope,
        entity.getId(),
        changes,
        reason,
        stringOr(body.get("ipAddress"), null),
        stringOr(body.get("userAgent"), null),
        "SOFT_DELETE");

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "SOFT_DELETED");
    event.put("at", now.toString());
    event.put("userId", scope.userId());
    event.put("role", scope.roleCode());
    event.put("message", reason);
    event.put("changes", changes);
    entity.getHistory().add(event);

    return toDto(repository.save(entity));
  }

  /** Restore soft-deleted student. Elevated roles only. */
  @Transactional
  public Map<String, Object> restore(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireElevated(scope);
    requireFeature(scope);
    StudentRecordEntity entity = requireStudent(id, scope.organizationId());
    if (!entity.isDeleted()) {
      throw new StudentException("VALIDATION", "Student is not deleted");
    }

    String reason = stringOr(body.get("reason"), "Student restored");
    String restoreStatus = stringOr(body.get("status"), "ACTIVE").toUpperCase(Locale.ROOT);
    if (STATUS_DELETED.equals(restoreStatus)) {
      restoreStatus = "ACTIVE";
    }
    assertAllowedStatus(restoreStatus);

    Instant now = Instant.now();
    String before = entity.getStatus();
    entity.setDeletedAt(null);
    entity.setDeletedBy(null);
    entity.setDeleteReason(null);
    entity.setRestoredAt(now);
    entity.setRestoredBy(scope.userId());
    entity.setStatus(restoreStatus);
    entity.setUpdatedAt(now);

    List<Map<String, Object>> changes = List.of(changeRow("status", before, restoreStatus));
    persistFieldAudits(
        scope,
        entity.getId(),
        changes,
        reason,
        stringOr(body.get("ipAddress"), null),
        stringOr(body.get("userAgent"), null),
        "RESTORE");

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "RESTORED");
    event.put("at", now.toString());
    event.put("userId", scope.userId());
    event.put("role", scope.roleCode());
    event.put("message", reason);
    event.put("changes", changes);
    entity.getHistory().add(event);

    return toDto(repository.save(entity));
  }

  /** Status change with audit. Prefer lifecycle for TC/dropout/alumni when possible. */
  @Transactional
  public Map<String, Object> changeStatus(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireStaffWrite(scope);
    requireFeature(scope);
    StudentRecordEntity entity = requireStudent(id, scope.organizationId());
    assertNotSoftDeleted(entity);

    String next = stringOr(body.get("status"), null);
    if (next == null) {
      throw new StudentException("VALIDATION", "status is required");
    }
    next = next.toUpperCase(Locale.ROOT);
    assertAllowedStatus(next);
    if (STATUS_DELETED.equals(next)) {
      throw new StudentException("VALIDATION", "Use soft-delete endpoint for DELETED status");
    }
    String reason = stringOr(body.get("reason"), "Status changed");
    String before = entity.getStatus();
    if (before.equalsIgnoreCase(next)) {
      return toDto(entity);
    }

    entity.setStatus(next);
    entity.setUpdatedAt(Instant.now());
    List<Map<String, Object>> changes = List.of(changeRow("status", before, next));
    persistFieldAudits(
        scope,
        entity.getId(),
        changes,
        reason,
        stringOr(body.get("ipAddress"), null),
        stringOr(body.get("userAgent"), null),
        "STATUS_CHANGE");

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "STATUS_CHANGED");
    event.put("at", Instant.now().toString());
    event.put("userId", scope.userId());
    event.put("role", scope.roleCode());
    event.put("message", reason);
    event.put("changes", changes);
    entity.getHistory().add(event);

    return toDto(repository.save(entity));
  }

  /**
   * Permanent delete — elevated only, soft-deleted first, blocked when operational history exists.
   */
  @Transactional
  public Map<String, Object> hardDelete(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireElevated(scope);
    requireFeature(scope);
    StudentRecordEntity entity = requireStudent(id, scope.organizationId());
    if (!entity.isDeleted()) {
      throw new StudentException(
          "VALIDATION", "Soft-delete the student first before permanent delete");
    }
    String reason = stringOr(body.get("reason"), null);
    if (reason == null || reason.isBlank()) {
      throw new StudentException("VALIDATION", "Permanent delete reason is required");
    }

    Map<String, Object> blockers = hardDeleteBlockers(scope, entity);
    if (!blockers.isEmpty()) {
      throw new StudentException(
          "DEPENDENCY",
          "Permanent delete blocked: " + String.join(", ", blockers.keySet()),
          blockers);
    }

    // Keep immutable audit rows (FK cascades from student_record — re-point by copying summary
    // event into org-level is not available; write final audit then delete).
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "HARD_DELETED");
    event.put("at", Instant.now().toString());
    event.put("userId", scope.userId());
    event.put("role", scope.roleCode());
    event.put("message", reason);
    entity.getHistory().add(event);
    repository.save(entity);

    Map<String, Object> snapshot = toDto(entity);
    repository.delete(entity);
    snapshot.put("permanentlyDeleted", true);
    return snapshot;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> fieldAudit(UUID id, Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    StudentRecordEntity entity = requireStudent(id, scope.organizationId());
    Map<String, Object> dto = toDto(entity);
    AccessScope access = relationshipAccess.resolve(scope);
    if (!access.allowsStudentDto(dto) && !PersonaRoles.isElevated(scope.roleCode())) {
      throw new StudentException("NOT_FOUND", "Student not found");
    }
    PageQuery q = PageQuery.of(page, size);
    var result =
        fieldAuditRepository.findByOrganizationIdAndStudentIdOrderByChangedAtDesc(
            scope.organizationId(), id, PageRequest.of(q.page(), q.size()));
    List<Map<String, Object>> items = result.getContent().stream().map(this::toAuditDto).toList();
    return PageResult.of(items, q.page(), q.size(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> timeline(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    StudentRecordEntity entity = requireStudent(id, scope.organizationId());
    Map<String, Object> dto = toDto(entity);
    AccessScope access = relationshipAccess.resolve(scope);
    if (!access.allowsStudentDto(dto) && !PersonaRoles.isElevated(scope.roleCode())) {
      throw new StudentException("NOT_FOUND", "Student not found");
    }
    List<Map<String, Object>> events = new ArrayList<>();
    if (entity.getHistory() != null) {
      for (Map<String, Object> h : entity.getHistory()) {
        Map<String, Object> row = new LinkedHashMap<>(h);
        row.put("source", "history");
        events.add(row);
      }
    }
    var audits =
        fieldAuditRepository.findByOrganizationIdAndStudentIdOrderByChangedAtDesc(
            scope.organizationId(), id, PageRequest.of(0, 200));
    for (StudentFieldAuditEntity a : audits) {
      Map<String, Object> row = toAuditDto(a);
      row.put("source", "field_audit");
      row.put("type", a.getEventType());
      row.put("at", a.getChangedAt().toString());
      row.put("message", a.getFieldName() + " changed");
      events.add(row);
    }
    events.sort(
        (a, b) ->
            String.valueOf(b.get("at")).compareToIgnoreCase(String.valueOf(a.get("at"))));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("studentId", id.toString());
    out.put("events", events);
    return out;
  }

  /**
   * Soft-delete many students. Body: { studentIds: [...], reason }. Soft-delete only — never hard
   * delete in bulk.
   */
  @Transactional
  public Map<String, Object> bulkSoftDelete(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireStaffWrite(scope);
    requireFeature(scope);
    List<UUID> ids = parseUuidList(body.get("studentIds"));
    if (ids.isEmpty()) {
      throw new StudentException("VALIDATION", "studentIds is required");
    }
    if (ids.size() > 200) {
      throw new StudentException("VALIDATION", "At most 200 students per bulk soft-delete");
    }
    String reason = stringOr(body.get("reason"), null);
    if (reason == null || reason.isBlank()) {
      throw new StudentException("VALIDATION", "Deletion reason is required");
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("reason", reason);
    meta.put("ipAddress", body.get("ipAddress"));
    meta.put("userAgent", body.get("userAgent"));

    int ok = 0;
    int skipped = 0;
    List<Map<String, Object>> errors = new ArrayList<>();
    for (UUID id : ids) {
      try {
        StudentRecordEntity entity = requireStudent(id, scope.organizationId());
        if (entity.isDeleted()) {
          skipped++;
          continue;
        }
        softDelete(id, meta);
        ok++;
      } catch (StudentException ex) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("id", id.toString());
        err.put("code", ex.getCode());
        err.put("message", ex.getMessage());
        errors.add(err);
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("requested", ids.size());
    out.put("deleted", ok);
    out.put("skipped", skipped);
    out.put("failed", errors.size());
    out.put("errors", errors);
    return out;
  }

  /** Restore many soft-deleted students. Elevated only. Body: { studentIds, reason?, status? }. */
  @Transactional
  public Map<String, Object> bulkRestore(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireElevated(scope);
    requireFeature(scope);
    List<UUID> ids = parseUuidList(body.get("studentIds"));
    if (ids.isEmpty()) {
      throw new StudentException("VALIDATION", "studentIds is required");
    }
    if (ids.size() > 200) {
      throw new StudentException("VALIDATION", "At most 200 students per bulk restore");
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("reason", stringOr(body.get("reason"), "Bulk restore"));
    meta.put("status", stringOr(body.get("status"), "ACTIVE"));
    meta.put("ipAddress", body.get("ipAddress"));
    meta.put("userAgent", body.get("userAgent"));

    int ok = 0;
    int skipped = 0;
    List<Map<String, Object>> errors = new ArrayList<>();
    for (UUID id : ids) {
      try {
        StudentRecordEntity entity = requireStudent(id, scope.organizationId());
        if (!entity.isDeleted()) {
          skipped++;
          continue;
        }
        restore(id, meta);
        ok++;
      } catch (StudentException ex) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("id", id.toString());
        err.put("code", ex.getCode());
        err.put("message", ex.getMessage());
        errors.add(err);
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("requested", ids.size());
    out.put("restored", ok);
    out.put("skipped", skipped);
    out.put("failed", errors.size());
    out.put("errors", errors);
    return out;
  }

  @SuppressWarnings("unchecked")
  private List<UUID> parseUuidList(Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      return List.of();
    }
    List<UUID> out = new ArrayList<>();
    for (Object item : list) {
      if (item == null) {
        continue;
      }
      try {
        out.add(UUID.fromString(String.valueOf(item)));
      } catch (IllegalArgumentException ignored) {
        // skip invalid
      }
    }
    return out;
  }

  private Map<String, Object> hardDeleteBlockers(TenantScope scope, StudentRecordEntity entity) {
    Map<String, Object> blockers = new LinkedHashMap<>();
    Map<String, Object> attendance =
        domainSnapshots.recentAttendance(scope, entity.getId(), entity.getAdmissionNo());
    if (truthyCount(attendance)) {
      blockers.put("attendance", attendance.get("count"));
    }
    Map<String, Object> exams =
        domainSnapshots.publishedMarks(scope, entity.getId(), entity.getAdmissionNo());
    if (truthyCount(exams)) {
      blockers.put("exams", exams.get("count"));
    }
    if (entity.getHistory() != null) {
      for (Map<String, Object> h : entity.getHistory()) {
        String type = stringOr(h.get("type"), "").toUpperCase(Locale.ROOT);
        if (type.contains("FEE")
            || type.contains("LIBRARY")
            || type.contains("TRANSPORT")
            || type.contains("HOSTEL")
            || type.contains("TC")
            || type.contains("CERTIFICATE")
            || type.contains("DISCIPLIN")) {
          blockers.put("history:" + type, h.get("message"));
        }
      }
    }
    return blockers;
  }

  private static boolean truthyCount(Map<String, Object> snap) {
    Object c = snap.get("count");
    if (c instanceof Number n) {
      return n.intValue() > 0;
    }
    Object items = snap.get("items");
    return items instanceof List<?> list && !list.isEmpty();
  }

  private void persistFieldAudits(
      TenantScope scope,
      UUID studentId,
      List<Map<String, Object>> changes,
      String reason,
      String ip,
      String ua,
      String eventType) {
    if (changes == null || changes.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    for (Map<String, Object> c : changes) {
      StudentFieldAuditEntity row = new StudentFieldAuditEntity();
      row.setId(UUID.randomUUID());
      row.setOrganizationId(scope.organizationId());
      row.setStudentId(studentId);
      row.setFieldName(stringOr(c.get("field"), "unknown"));
      row.setOldValue(c.get("from") == null ? null : String.valueOf(c.get("from")));
      row.setNewValue(c.get("to") == null ? null : String.valueOf(c.get("to")));
      row.setChangedBy(scope.userId());
      row.setChangedRole(scope.roleCode());
      row.setChangedAt(now);
      row.setReason(reason);
      row.setIpAddress(ip);
      row.setUserAgent(ua);
      row.setEventType(eventType);
      fieldAuditRepository.save(row);
    }
  }

  private List<Map<String, Object>> diffAnswers(
      Map<String, Object> before, Map<String, Object> after) {
    List<Map<String, Object>> changes = new ArrayList<>();
    Set<String> keys = new java.util.LinkedHashSet<>();
    keys.addAll(before.keySet());
    keys.addAll(after.keySet());
    for (String key : keys) {
      if ("guardians".equals(key)) {
        continue;
      }
      String oldV = normalizeScalar(before.get(key));
      String newV = normalizeScalar(after.get(key));
      if (!Objects.equals(oldV, newV)) {
        changes.add(changeRow(key, oldV, newV));
      }
    }
    return changes;
  }

  private static Map<String, Object> changeRow(String field, Object from, Object to) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("field", field);
    row.put("from", from);
    row.put("to", to);
    return row;
  }

  private static String normalizeScalar(Object v) {
    if (v == null) {
      return "";
    }
    if (v instanceof Map || v instanceof List) {
      return String.valueOf(v);
    }
    return String.valueOf(v).trim();
  }

  private void validateFormats(Map<String, Object> answers) {
    String mobile = stringOr(answers.get("mobile"), null);
    if (mobile != null && !MOBILE_RE.matcher(mobile).matches()) {
      throw new StudentException("VALIDATION", "Invalid mobile number");
    }
    String alt = stringOr(answers.get("alternateMobile"), null);
    if (alt != null && !MOBILE_RE.matcher(alt).matches()) {
      throw new StudentException("VALIDATION", "Invalid alternate mobile number");
    }
    String email = stringOr(answers.get("email"), null);
    if (email != null && !EMAIL_RE.matcher(email).matches()) {
      throw new StudentException("VALIDATION", "Invalid email");
    }
    String pin = stringOr(answers.get("pinCode"), stringOr(answers.get("pincode"), null));
    if (pin != null && !PIN_RE.matcher(pin).matches()) {
      throw new StudentException("VALIDATION", "Invalid PIN code");
    }
    validateDateField(answers, "dateOfBirth", "DOB");
    validateDateField(answers, "dob", "DOB");
    validateDateField(answers, "admissionDate", "Admission date");
  }

  private void validateDateField(Map<String, Object> answers, String key, String label) {
    String raw = stringOr(answers.get(key), null);
    if (raw == null) {
      return;
    }
    try {
      LocalDate d = LocalDate.parse(raw.length() >= 10 ? raw.substring(0, 10) : raw);
      if (d.isAfter(LocalDate.now().plusDays(1))) {
        throw new StudentException("VALIDATION", label + " cannot be in the future");
      }
    } catch (DateTimeParseException ex) {
      // Allow non-ISO display formats already stored; only reject clearly future ISO dates
    }
  }

  private void assertUniqueAdmissionNo(String org, String admissionNo, UUID selfId) {
    if (admissionNo == null || admissionNo.isBlank()) {
      return;
    }
    repository
        .findByOrganizationIdAndAdmissionNoIgnoreCaseAndDeletedAtIsNull(org, admissionNo)
        .ifPresent(
            other -> {
              if (!other.getId().equals(selfId)) {
                throw new StudentException(
                    "VALIDATION", "Admission number already exists: " + admissionNo);
              }
            });
  }

  private void assertUniqueAadhaar(String org, Map<String, Object> answers, UUID selfId) {
    String aadhaar = stringOr(answers.get("aadhaar"), stringOr(answers.get("aadhaarNumber"), null));
    if (aadhaar == null || aadhaar.isBlank()) {
      return;
    }
    for (StudentRecordEntity e :
        repository.findByOrganizationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(org)) {
      if (e.getId().equals(selfId)) {
        continue;
      }
      String other =
          stringOr(e.getAnswers().get("aadhaar"), stringOr(e.getAnswers().get("aadhaarNumber"), ""));
      if (aadhaar.equalsIgnoreCase(other)) {
        throw new StudentException("VALIDATION", "Aadhaar already exists on another student");
      }
    }
  }

  private void assertUniqueRollInClass(String org, Map<String, Object> answers, UUID selfId) {
    String roll = stringOr(answers.get("rollNo"), stringOr(answers.get("rollNumber"), null));
    String cls =
        stringOr(answers.get("classApplied"), stringOr(answers.get("classSection"), null));
    if (roll == null || cls == null) {
      return;
    }
    for (StudentRecordEntity e :
        repository.findByOrganizationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(org)) {
      if (e.getId().equals(selfId)) {
        continue;
      }
      String otherRoll =
          stringOr(e.getAnswers().get("rollNo"), stringOr(e.getAnswers().get("rollNumber"), ""));
      String otherCls =
          stringOr(
              e.getAnswers().get("classApplied"),
              stringOr(e.getAnswers().get("classSection"), ""));
      if (roll.equalsIgnoreCase(otherRoll) && cls.equalsIgnoreCase(otherCls)) {
        throw new StudentException(
            "VALIDATION", "Roll number already exists in class " + cls + ": " + roll);
      }
    }
  }

  private void assertAllowedStatus(String status) {
    if (!ALLOWED_STATUSES.contains(status)) {
      throw new StudentException("VALIDATION", "Unsupported status: " + status);
    }
  }

  private void assertNotSoftDeleted(StudentRecordEntity entity) {
    if (entity.isDeleted()) {
      throw new StudentException(
          "VALIDATION", "Student is deleted. Restore before editing or changing status.");
    }
  }

  private void requireStaffWrite(TenantScope scope) {
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
  }

  private void requireElevated(TenantScope scope) {
    requireStaffWrite(scope);
    if (!PersonaRoles.isElevated(scope.roleCode())) {
      throw new StudentException(
          "FORBIDDEN", "Elevated role required (ADMIN / PRINCIPAL / SUPER_ADMIN / SHOP_OWNER)");
    }
  }

  private Map<String, Object> toAuditDto(StudentFieldAuditEntity a) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", a.getId().toString());
    dto.put("studentId", a.getStudentId().toString());
    dto.put("field", a.getFieldName());
    dto.put("fieldName", a.getFieldName());
    dto.put("oldValue", a.getOldValue());
    dto.put("newValue", a.getNewValue());
    dto.put("from", a.getOldValue());
    dto.put("to", a.getNewValue());
    dto.put("changedBy", a.getChangedBy());
    dto.put("changedRole", a.getChangedRole());
    dto.put("changedAt", a.getChangedAt().toString());
    dto.put("reason", a.getReason());
    dto.put("ipAddress", a.getIpAddress());
    dto.put("userAgent", a.getUserAgent());
    dto.put("eventType", a.getEventType());
    return dto;
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
    // Account-link metadata is system-managed relationship data, not a configurable form field.
    // Preserve it so parent access scoping and IN_APP delivery can resolve the guardian login.
    for (String identityKey : List.of("userId", "authUsername", "username")) {
      String identity = stringOr(raw.get(identityKey), null);
      if (identity != null && !identity.isBlank()) {
        out.put(identityKey, identity);
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
    // House assignment is part of student identity even when an older
    // student_master form definition does not yet declare the field.
    if (source.containsKey("house")) {
      out.put("house", source.get("house"));
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

  private List<StudentRecordEntity> loadCandidates(TenantScope scope) {
    if (scope.branchId() != null && !scope.branchId().isBlank()
        && scope.academicSessionId() != null && !scope.academicSessionId().isBlank()) {
      return repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId());
    }
    return repository.findByOrganizationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(scope.organizationId());
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
    dto.put("deleted", e.isDeleted());
    dto.put("deletedAt", e.getDeletedAt() != null ? e.getDeletedAt().toString() : null);
    dto.put("deletedBy", e.getDeletedBy());
    dto.put("deleteReason", e.getDeleteReason());
    dto.put("restoredAt", e.getRestoredAt() != null ? e.getRestoredAt().toString() : null);
    dto.put("restoredBy", e.getRestoredBy());
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
