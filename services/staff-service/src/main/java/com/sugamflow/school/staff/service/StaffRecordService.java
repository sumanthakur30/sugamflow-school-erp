package com.sugamflow.school.staff.service;

import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.staff.config.StaffProperties;
import com.sugamflow.school.staff.directory.StaffDirectoryCatalog;
import com.sugamflow.school.staff.integration.ConfigEngineClient;
import com.sugamflow.school.staff.persistence.entity.StaffRecordEntity;
import com.sugamflow.school.staff.persistence.repo.StaffRecordRepository;
import com.sugamflow.school.staff.web.StaffException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
public class StaffRecordService {

  public static final String FEATURE_STAFF_MASTER = "FEATURE_STAFF_MASTER";
  public static final String MODULE_STAFF = "staff";

  private final StaffRecordRepository repository;
  private final ConfigEngineClient engines;
  private final StaffProperties properties;

  public StaffRecordService(
      StaffRecordRepository repository, ConfigEngineClient engines, StaffProperties properties) {
    this.repository = repository;
    this.engines = engines;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STAFF);
    String formKey = resolveFormKey(module);
    Map<String, Object> form = engines.getForm(scope, formKey);
    if (form == null) {
      throw new StaffException("FORM_MISSING", "Form definition not found: " + formKey);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("module", module);
    out.put("formKey", formKey);
    out.put("form", form);
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> directoryBootstrap() {
    TenantScope scope = TenantContext.require();
    boolean enabled = engines.isFeatureEnabled(scope, FEATURE_STAFF_MASTER);
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STAFF);
    Map<String, Object> settings = moduleSettingsMap(module);

    Map<String, Object> out = StaffDirectoryCatalog.defaultBootstrap();
    out.put("featureEnabled", enabled);
    out.put("organizationId", scope.organizationId());
    out.put("branchId", scope.branchId());
    if (settings.get("directoryColumns") instanceof List<?> cols && !cols.isEmpty()) {
      out.put("columns", cols);
    }
    if (settings.get("directoryFilters") instanceof List<?> filters && !filters.isEmpty()) {
      out.put("filters", filters);
    }
    if (settings.get("directoryQuickActions") instanceof List<?> actions && !actions.isEmpty()) {
      out.put("quickActions", actions);
    }
    out.put("moduleSettings", settings);
    return out;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    PageQuery q = PageQuery.of(page, size);
    Pageable pageable = PageRequest.of(q.page(), q.size());
    Page<StaffRecordEntity> result;
    if (notBlank(scope.branchId())) {
      result =
          repository.findByOrganizationIdAndBranchIdOrderByUpdatedAtDesc(
              scope.organizationId(), scope.branchId(), pageable);
    } else {
      result = repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId(), pageable);
    }
    return PageResult.of(result.map(this::toDto).getContent(), q.page(), q.size(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return toDto(requireStaff(id, scope.organizationId()));
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    requireModuleEnabled(scope);

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STAFF);
    String formKey = stringOr(body.get("formKey"), resolveFormKey(module));
    Map<String, Object> form = engines.getForm(scope, formKey);
    if (form == null) {
      throw new StaffException("FORM_MISSING", "Form definition not found: " + formKey);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> answers =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();

    validateMandatory(form, answers);

    String employeeNo = stringOr(answers.get("employeeNo"), stringOr(body.get("employeeNo"), null));
    if (employeeNo == null || employeeNo.isBlank()) {
      employeeNo = generateEmployeeNo(scope);
    }
    answers.put("employeeNo", employeeNo);

    StaffRecordEntity entity = new StaffRecordEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setFormKey(formKey);
    entity.setStatus("ACTIVE");
    entity.setEmployeeNo(employeeNo);
    entity.setAnswers(answers);
    entity.setCreatedBy(scope.userId());
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    List<Map<String, Object>> history = new ArrayList<>();
    history.add(event(scope, "CREATED", "Staff record created"));
    entity.setHistory(history);

    return toDto(repository.save(entity));
  }

  @Transactional
  public Map<String, Object> update(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    StaffRecordEntity entity = requireStaff(id, scope.organizationId());

    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STAFF);
    String formKey = stringOr(entity.getFormKey(), resolveFormKey(module));
    Map<String, Object> form = engines.getForm(scope, formKey);

    @SuppressWarnings("unchecked")
    Map<String, Object> incoming =
        body.get("answers") instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();

    Map<String, Object> answers = new LinkedHashMap<>(entity.getAnswers());
    answers.putAll(incoming);
    if (form != null) {
      validateMandatory(form, answers);
    }

    String employeeNo = stringOr(answers.get("employeeNo"), entity.getEmployeeNo());
    answers.put("employeeNo", employeeNo);

    entity.setAnswers(answers);
    entity.setEmployeeNo(employeeNo);
    if (body.get("status") != null && !String.valueOf(body.get("status")).isBlank()) {
      entity.setStatus(String.valueOf(body.get("status")));
    }
    entity.setUpdatedAt(Instant.now());
    entity.getHistory().add(event(scope, "UPDATED", "Staff record updated"));

    return toDto(repository.save(entity));
  }

  @Transactional
  public Map<String, Object> softDelete(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    StaffRecordEntity entity = requireStaff(id, scope.organizationId());
    if ("DELETED".equalsIgnoreCase(entity.getStatus())) {
      return toDto(entity);
    }
    entity.setStatus("DELETED");
    entity.setUpdatedAt(Instant.now());
    entity.getHistory().add(event(scope, "DELETED", "Staff record soft-deleted"));
    return toDto(repository.save(entity));
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> search(Map<String, String> params) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    PageQuery q = PageQuery.of(parseInt(params.get("page")), parseInt(params.get("size")));
    DirectoryQuery dq = DirectoryQuery.from(params, scope);
    Page<StaffRecordEntity> page =
        repository.searchDirectory(
            scope.organizationId(),
            nullToEmpty(dq.branch),
            blank(dq.branch),
            nullToEmpty(dq.status),
            blank(dq.status),
            nullToEmpty(dq.q),
            blank(dq.q),
            nullToEmpty(dq.department),
            blank(dq.department),
            nullToEmpty(dq.designation),
            blank(dq.designation),
            nullToEmpty(dq.employmentType),
            blank(dq.employmentType),
            nullToEmpty(dq.gender),
            blank(dq.gender),
            PageRequest.of(q.page(), q.size()));
    List<Map<String, Object>> items = page.getContent().stream().map(this::toRow).toList();
    return PageResult.of(items, q.page(), q.size(), page.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    boolean scoped = notBlank(scope.branchId());
    long total;
    long active;
    long inactive;
    if (scoped) {
      total = repository.countByOrganizationIdAndBranchId(scope.organizationId(), scope.branchId());
      active =
          repository.countByOrganizationIdAndBranchIdAndStatus(
              scope.organizationId(), scope.branchId(), "ACTIVE");
      inactive =
          repository.countByOrganizationIdAndBranchIdAndStatus(
              scope.organizationId(), scope.branchId(), "INACTIVE");
    } else {
      total = repository.countByOrganizationId(scope.organizationId());
      active = repository.countByOrganizationIdAndStatus(scope.organizationId(), "ACTIVE");
      inactive = repository.countByOrganizationIdAndStatus(scope.organizationId(), "INACTIVE");
    }

    List<StaffRecordEntity> sample =
        scoped
            ? repository.findByOrganizationIdAndBranchIdOrderByUpdatedAtDesc(
                scope.organizationId(), scope.branchId())
            : repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId());

    long male = 0;
    long female = 0;
    long newJoinees = 0;
    long teachers = 0;
    long nonTeaching = 0;
    Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
    Map<String, Long> byDepartment = new LinkedHashMap<>();
    Map<String, Long> byDesignation = new LinkedHashMap<>();
    Map<String, Long> byBranch = new LinkedHashMap<>();
    long deleted = 0;
    for (StaffRecordEntity e : sample) {
      if ("DELETED".equalsIgnoreCase(e.getStatus())) {
        deleted++;
        continue;
      }
      String gender = stringVal(e.getAnswers(), "gender").toLowerCase();
      if (gender.startsWith("m")) {
        male++;
      } else if (gender.startsWith("f")) {
        female++;
      }
      if (e.getCreatedAt() != null && e.getCreatedAt().isAfter(since)) {
        newJoinees++;
      }
      String designation = stringVal(e.getAnswers(), "designation");
      String designationLower = designation.toLowerCase();
      if (designationLower.contains("teacher")
          || designationLower.contains("principal")
          || designationLower.contains("vice principal")
          || designationLower.contains("coordinator")) {
        teachers++;
      } else {
        nonTeaching++;
      }
      String department = stringVal(e.getAnswers(), "department");
      if (!department.isBlank()) {
        byDepartment.merge(department, 1L, Long::sum);
      }
      if (!designation.isBlank()) {
        byDesignation.merge(designation, 1L, Long::sum);
      }
      String branch = e.getBranchId() != null ? e.getBranchId() : "unknown";
      byBranch.merge(branch, 1L, Long::sum);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("total", Math.max(0, total - deleted));
    out.put("active", active);
    out.put("inactive", inactive);
    out.put("male", male);
    out.put("female", female);
    out.put("teachers", teachers);
    out.put("nonTeaching", nonTeaching);
    out.put("newJoinees", newJoinees);
    out.put("newJoinings", newJoinees);
    out.put("byDepartment", byDepartment);
    out.put("byDesignation", byDesignation);
    out.put("byBranch", byBranch);
    out.put(
        "scope",
        Map.of("organizationId", scope.organizationId(), "branchId", scope.branchId()));
    return out;
  }

  @Transactional(readOnly = true)
  public byte[] exportCsv(Map<String, String> params) {
    Map<String, String> exportParams = new LinkedHashMap<>(params != null ? params : Map.of());
    exportParams.put("page", "0");
    exportParams.put("size", "200");
    PageResult<Map<String, Object>> page = search(exportParams);
    StringBuilder sb = new StringBuilder();
    sb.append("employeeNo,fullName,department,designation,employmentType,gender,status,mobile,email,branchId\n");
    for (Map<String, Object> row : page.items()) {
      sb.append(csv(row.get("employeeNo"))).append(',')
          .append(csv(row.get("fullName"))).append(',')
          .append(csv(row.get("department"))).append(',')
          .append(csv(row.get("designation"))).append(',')
          .append(csv(row.get("employmentType"))).append(',')
          .append(csv(row.get("gender"))).append(',')
          .append(csv(row.get("status"))).append(',')
          .append(csv(row.get("mobile"))).append(',')
          .append(csv(row.get("email"))).append(',')
          .append(csv(row.get("branchId"))).append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String generateEmployeeNo(TenantScope scope) {
    String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    String branch =
        notBlank(scope.branchId())
            ? scope.branchId().toUpperCase().replaceAll("[^A-Z0-9]", "")
            : "MAIN";
    if (branch.length() > 6) {
      branch = branch.substring(0, 6);
    }
    return "EMP-" + branch + "-" + suffix;
  }

  private Map<String, Object> event(TenantScope scope, String type, String message) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("type", type);
    e.put("at", Instant.now().toString());
    e.put("userId", scope.userId());
    e.put("role", scope.roleCode());
    e.put("message", message);
    return e;
  }

  private Map<String, Object> toRow(StaffRecordEntity e) {
    Map<String, Object> answers = e.getAnswers() != null ? e.getAnswers() : Map.of();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", e.getId().toString());
    row.put("employeeNo", e.getEmployeeNo());
    row.put("fullName", stringVal(answers, "fullName"));
    row.put("authUsername", stringVal(answers, "authUsername"));
    row.put("department", stringVal(answers, "department"));
    row.put("designation", stringVal(answers, "designation"));
    row.put("employmentType", stringVal(answers, "employmentType"));
    row.put("gender", stringVal(answers, "gender"));
    row.put("status", e.getStatus());
    row.put("mobile", stringVal(answers, "mobile"));
    row.put("email", stringVal(answers, "email"));
    row.put("joiningDate", stringVal(answers, "joiningDate"));
    row.put("branchId", e.getBranchId());
    row.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
    return row;
  }

  private void requireFeature(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FEATURE_STAFF_MASTER)) {
      throw new StaffException(
          "FEATURE_DISABLED", "FEATURE_STAFF_MASTER is off for this subscription plan.");
    }
  }

  private void requireModuleEnabled(TenantScope scope) {
    Map<String, Object> settings = moduleSettingsMap(engines.getModuleSettings(scope, MODULE_STAFF));
    if (Boolean.FALSE.equals(settings.get("enabled"))) {
      throw new StaffException("MODULE_DISABLED", "Staff module is disabled in module settings.");
    }
  }

  private String resolveFormKey(Map<String, Object> module) {
    Object configured = moduleSettingsMap(module).get("formKey");
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

  private StaffRecordEntity requireStaff(UUID id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new StaffException("NOT_FOUND", "Staff record not found"));
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
        if ("employeeNo".equals(key)) {
          continue;
        }
        Object value = answers.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
          Object label = field.get("label");
          throw new StaffException(
              "VALIDATION", "Mandatory field missing: " + (label != null ? label : key));
        }
      }
    }
  }

  private static boolean blank(String v) {
    return v == null || v.isBlank();
  }

  private static boolean notBlank(String v) {
    return !blank(v);
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private static Integer parseInt(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String stringVal(Map<String, Object> answers, String key) {
    if (answers == null || answers.get(key) == null) {
      return "";
    }
    return String.valueOf(answers.get(key)).trim();
  }

  private static String csv(Object v) {
    String s = v == null ? "" : String.valueOf(v);
    if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? fallback : s;
  }

  private Map<String, Object> toDto(StaffRecordEntity e) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", e.getId().toString());
    dto.put("organizationId", e.getOrganizationId());
    dto.put("branchId", e.getBranchId());
    dto.put("formKey", e.getFormKey());
    dto.put("status", e.getStatus());
    dto.put("employeeNo", e.getEmployeeNo());
    dto.put("answers", e.getAnswers());
    dto.put("history", e.getHistory());
    dto.put("createdBy", e.getCreatedBy());
    dto.put("createdAt", e.getCreatedAt().toString());
    dto.put("updatedAt", e.getUpdatedAt().toString());
    return dto;
  }

  private record DirectoryQuery(
      String branch,
      String status,
      String q,
      String department,
      String designation,
      String employmentType,
      String gender) {

    static DirectoryQuery from(Map<String, String> params, TenantScope scope) {
      Map<String, String> p = params != null ? params : Map.of();
      String branch = first(p.get("branchId"), scope.branchId());
      return new DirectoryQuery(
          branch,
          emptyToNull(p.get("status")),
          emptyToNull(p.get("q")),
          emptyToNull(p.get("department")),
          emptyToNull(p.get("designation")),
          emptyToNull(p.get("employmentType")),
          emptyToNull(p.get("gender")));
    }

    private static String first(String a, String b) {
      if (a != null && !a.isBlank()) {
        return a;
      }
      return b;
    }

    private static String emptyToNull(String v) {
      return v == null || v.isBlank() ? null : v.trim();
    }
  }
}
