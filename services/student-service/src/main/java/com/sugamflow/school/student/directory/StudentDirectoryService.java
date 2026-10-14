package com.sugamflow.school.student.directory;

import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.api.PageResults;
import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.service.RelationshipAccessService;
import com.sugamflow.school.student.service.StudentRecordService;
import com.sugamflow.school.student.sheet.StudentGuardianSync;
import com.sugamflow.school.student.sheet.StudentSheetCatalog;
import com.sugamflow.school.student.web.StudentException;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDirectoryService {

  public static final String FEATURE_STUDENT_MASTER = "FEATURE_STUDENT_MASTER";
  public static final String MODULE_STUDENT = "student";

  private final StudentRecordRepository repository;
  private final ConfigEngineClient engines;
  private final RelationshipAccessService relationshipAccess;
  private final SensitiveExportAuditService exportAudit;

  public StudentDirectoryService(
      StudentRecordRepository repository,
      ConfigEngineClient engines,
      RelationshipAccessService relationshipAccess,
      SensitiveExportAuditService exportAudit) {
    this.repository = repository;
    this.engines = engines;
    this.relationshipAccess = relationshipAccess;
    this.exportAudit = exportAudit;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    boolean enabled = engines.isFeatureEnabled(scope, FEATURE_STUDENT_MASTER);
    Map<String, Object> module = engines.getModuleSettings(scope, MODULE_STUDENT);
    Map<String, Object> settings = moduleSettings(module);

    Map<String, Object> out = DirectoryCatalog.defaultBootstrap();
    out.put("featureEnabled", enabled);
    out.put("organizationId", scope.organizationId());
    out.put("branchId", scope.branchId());
    out.put("academicSessionId", scope.academicSessionId());
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
  public PageResult<Map<String, Object>> search(Map<String, String> params) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    PageQuery q = PageQuery.of(parseInt(params.get("page")), parseInt(params.get("size")));
    DirectoryQuery dq = DirectoryQuery.from(params, scope);
    Boolean includeDeleted = parseIncludeDeleted(params.get("includeDeleted"));
    if (Boolean.TRUE.equals(includeDeleted) && !PersonaRoles.isElevated(scope.roleCode())) {
      throw new StudentException(
          "FORBIDDEN", "Only elevated roles can view deleted students (trash)");
    }
    Page<StudentRecordEntity> page =
        repository.searchDirectory(
            scope.organizationId(),
            nullToEmpty(dq.branch),
            blank(dq.branch),
            nullToEmpty(dq.session),
            blank(dq.session),
            nullToEmpty(dq.status),
            blank(dq.status),
            nullToEmpty(dq.q),
            blank(dq.q),
            nullToEmpty(dq.classSection),
            blank(dq.classSection),
            nullToEmpty(dq.classLike),
            blank(dq.classLike),
            nullToEmpty(dq.gender),
            blank(dq.gender),
            nullToEmpty(dq.category),
            blank(dq.category),
            nullToEmpty(dq.house),
            blank(dq.house),
            !dq.transportOnly,
            !dq.hostelOnly,
            !dq.scholarshipOnly,
            includeDeleted,
            PageRequest.of(q.page(), q.size()));
    List<Map<String, Object>> items = page.getContent().stream().map(this::toRow).toList();
    AccessScope access = relationshipAccess.resolve(scope);
    if (access.restricted()) {
      return PageResults.filterThenPage(items, access::allowsStudentDto, q.page(), q.size());
    }
    return PageResult.of(items, q.page(), q.size(), page.getTotalElements());
  }

  /** Distinct class and section labels stored on students in the current tenant scope. */
  @Transactional(readOnly = true)
  public Map<String, Object> classOptions(Map<String, String> params) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    DirectoryQuery dq = DirectoryQuery.from(params, scope);
    String status = dq.status() != null ? dq.status() : "ACTIVE";
    List<String> labels =
        repository.distinctClassLabels(
            scope.organizationId(),
            nullToEmpty(dq.branch()),
            blank(dq.branch()),
            nullToEmpty(dq.session()),
            blank(dq.session()),
            status,
            false);
    List<Map<String, Object>> classes = new ArrayList<>();
    for (ClassLabelGroups.ClassGroup group : ClassLabelGroups.group(labels)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", group.name());
      List<Map<String, Object>> sections = new ArrayList<>();
      for (ClassLabelGroups.SectionGroup section : group.sections()) {
        Map<String, Object> sectionRow = new LinkedHashMap<>();
        sectionRow.put("name", section.name());
        sectionRow.put("label", section.label());
        sections.add(sectionRow);
      }
      row.put("sections", sections);
      classes.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("classes", classes);
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    boolean scoped =
        notBlank(scope.branchId()) && notBlank(scope.academicSessionId());
    long total;
    long active;
    long inactive;
    long alumni;
    long tc;
    if (scoped) {
      total =
          repository.countByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNull(
              scope.organizationId(), scope.branchId(), scope.academicSessionId());
      active =
          repository.countByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusAndDeletedAtIsNull(
              scope.organizationId(), scope.branchId(), scope.academicSessionId(), "ACTIVE");
      inactive =
          repository.countByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusAndDeletedAtIsNull(
              scope.organizationId(), scope.branchId(), scope.academicSessionId(), "INACTIVE");
      alumni =
          repository.countByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusAndDeletedAtIsNull(
              scope.organizationId(), scope.branchId(), scope.academicSessionId(), "ALUMNI");
      tc =
          repository.countByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusAndDeletedAtIsNull(
              scope.organizationId(), scope.branchId(), scope.academicSessionId(), "TC_ISSUED");
    } else {
      total = repository.countByOrganizationIdAndDeletedAtIsNull(scope.organizationId());
      active = repository.countByOrganizationIdAndStatusAndDeletedAtIsNull(scope.organizationId(), "ACTIVE");
      inactive =
          repository.countByOrganizationIdAndStatusAndDeletedAtIsNull(scope.organizationId(), "INACTIVE");
      alumni = repository.countByOrganizationIdAndStatusAndDeletedAtIsNull(scope.organizationId(), "ALUMNI");
      tc = repository.countByOrganizationIdAndStatusAndDeletedAtIsNull(scope.organizationId(), "TC_ISSUED");
    }

    List<StudentRecordEntity> sample =
        scoped
            ? repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                scope.organizationId(), scope.branchId(), scope.academicSessionId())
            : repository.findByOrganizationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(scope.organizationId());

    long boys = 0;
    long girls = 0;
    long newAdmissions = 0;
    Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
    Map<String, Long> byClass = new LinkedHashMap<>();
    Map<String, Long> byBranch = new LinkedHashMap<>();
    for (StudentRecordEntity e : sample) {
      String gender = stringVal(e.getAnswers(), "gender").toLowerCase();
      if (gender.startsWith("m") || "boy".equals(gender)) {
        boys++;
      } else if (gender.startsWith("f") || "girl".equals(gender)) {
        girls++;
      }
      if (e.getCreatedAt() != null && e.getCreatedAt().isAfter(since)) {
        newAdmissions++;
      }
      String cls =
          firstNonBlank(
              stringVal(e.getAnswers(), "classSection"), stringVal(e.getAnswers(), "classApplied"));
      if (!cls.isBlank()) {
        byClass.merge(cls, 1L, Long::sum);
      }
      String branch = e.getBranchId() != null ? e.getBranchId() : "unknown";
      byBranch.merge(branch, 1L, Long::sum);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("total", total);
    out.put("active", active);
    out.put("inactive", inactive);
    out.put("alumni", alumni);
    out.put("tcIssued", tc);
    out.put("boys", boys);
    out.put("girls", girls);
    out.put("newAdmissions", newAdmissions);
    out.put("byClass", byClass);
    out.put("byBranch", byBranch);
    // Helps explain "dashboard 0 but students exist" when campus/session headers don't match rows.
    long activeOrgWide =
        repository.countByOrganizationIdAndStatusAndDeletedAtIsNull(scope.organizationId(), "ACTIVE");
    long totalOrgWide = repository.countByOrganizationIdAndDeletedAtIsNull(scope.organizationId());
    out.put("activeOrgWide", activeOrgWide);
    out.put("totalOrgWide", totalOrgWide);
    out.put(
        "scope",
        Map.of(
            "organizationId", scope.organizationId(),
            "branchId", scope.branchId() == null ? "" : scope.branchId(),
            "academicSessionId",
                scope.academicSessionId() == null ? "" : scope.academicSessionId(),
            "scoped", scoped));
    return out;
  }

  @Transactional(readOnly = true)
  public byte[] exportCsv(Map<String, String> params) {
    Map<String, String> exportParams = new LinkedHashMap<>(params != null ? params : Map.of());
    exportParams.put("page", "0");
    exportParams.put("size", "2000");
    PageResult<Map<String, Object>> page = search(exportParams);
    List<StudentSheetCatalog.Col> columns = StudentSheetCatalog.directoryColumns();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(columns.get(i).label());
    }
    sb.append('\n');
    for (Map<String, Object> row : page.items()) {
      for (int i = 0; i < columns.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append(StudentSheetCatalog.csv(exportValue(row, columns.get(i).key())));
      }
      sb.append('\n');
    }
    exportAudit.record("DIRECTORY_CSV", false, page.items().size());
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> exportWorkbook(Map<String, String> params, String format) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    Map<String, String> exportParams = new LinkedHashMap<>(params != null ? params : Map.of());
    exportParams.put("page", "0");
    exportParams.put("size", "2000");
    PageResult<Map<String, Object>> page = search(exportParams);
    List<Map<String, Object>> columns = new java.util.ArrayList<>();
    List<Map<String, Object>> rows = new java.util.ArrayList<>();
    for (StudentSheetCatalog.Col col : StudentSheetCatalog.directoryColumns()) {
      columns.add(Map.of("key", col.key(), "label", col.label()));
    }
    for (Map<String, Object> source : page.items()) {
      Map<String, Object> copy = new LinkedHashMap<>();
      for (StudentSheetCatalog.Col col : StudentSheetCatalog.directoryColumns()) {
        copy.put(col.key(), StudentSheetCatalog.formulaSafe(exportValue(source, col.key())));
      }
      rows.add(copy);
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("title", "Student Directory");
    data.put("subtitle", scope.organizationId() + " · " + page.items().size() + " students");
    data.put("columns", columns);
    data.put("rows", rows);
    Map<String, Object> rendered =
        engines.renderReport(scope, "student_directory", data, format == null ? "EXCEL" : format);
    if (rendered == null || rendered.get("contentBase64") == null) {
      throw new StudentException("RENDER_FAILED", "Directory export render returned no content");
    }
    exportAudit.record("DIRECTORY_WORKBOOK", false, page.items().size());
    return rendered;
  }

  private Map<String, Object> toRow(StudentRecordEntity e) {
    Map<String, Object> answers = e.getAnswers() != null ? e.getAnswers() : Map.of();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", e.getId().toString());
    row.put("admissionNo", e.getAdmissionNo());
    row.put("fullName", firstNonBlank(stringVal(answers, "fullName"), stringVal(answers, "studentName")));
    row.put(
        "classSection",
        firstNonBlank(stringVal(answers, "classSection"), stringVal(answers, "classApplied")));
    row.put("gender", stringVal(answers, "gender"));
    row.put("dateOfBirth", firstNonBlank(stringVal(answers, "dateOfBirth"), stringVal(answers, "dob")));
    row.put("bloodGroup", stringVal(answers, "bloodGroup"));
    row.put("admissionDate", stringVal(answers, "admissionDate"));
    row.put("classGrade", stringVal(answers, "classGrade"));
    row.put("sectionLetter", firstNonBlank(stringVal(answers, "sectionLetter"), stringVal(answers, "section")));
    row.put("status", e.getStatus());
    row.put("mobile", stringVal(answers, "mobile"));
    row.put("email", stringVal(answers, "email"));
    row.put("category", stringVal(answers, "category"));
    row.put("house", stringVal(answers, "house"));
    row.put("rollNo", stringVal(answers, "rollNo"));
    row.put(
        "aadhaar",
        StudentRecordService.maskAadhaar(
            firstNonBlank(stringVal(answers, "aadhaar"), stringVal(answers, "aadhaarNumber"))));
    row.put("penNumber", stringVal(answers, "penNumber"));
    row.put("apaarId", firstNonBlank(stringVal(answers, "apaarId"), stringVal(answers, "apaarNumber")));
    row.put("samagraId", stringVal(answers, "samagraId"));
    row.put("schoolStudentId", stringVal(answers, "schoolStudentId"));
    row.put("photoUrl", StudentRecordService.displayPhotoUrl(answers));
    row.put("parentName", parentName(answers));
    row.put("fatherName", StudentGuardianSync.nameForRelation(answers, "father"));
    row.put("fatherMobile", stringVal(answers, "fatherMobile"));
    row.put("fatherEmail", stringVal(answers, "fatherEmail"));
    row.put("fatherOccupation", stringVal(answers, "fatherOccupation"));
    row.put("motherName", StudentGuardianSync.nameForRelation(answers, "mother"));
    row.put("motherMobile", stringVal(answers, "motherMobile"));
    row.put("motherEmail", stringVal(answers, "motherEmail"));
    row.put("motherOccupation", stringVal(answers, "motherOccupation"));
    row.put("address", stringVal(answers, "address"));
    row.put("city", stringVal(answers, "city"));
    row.put("state", stringVal(answers, "state"));
    row.put("pinCode", firstNonBlank(stringVal(answers, "pinCode"), stringVal(answers, "pincode")));
    row.put("nationality", stringVal(answers, "nationality"));
    row.put("religion", stringVal(answers, "religion"));
    row.put("previousSchool", stringVal(answers, "previousSchool"));
    row.put("previousClass", stringVal(answers, "previousClass"));
    row.put("rte", truthy(answers.get("rte")) || "RTE".equalsIgnoreCase(stringVal(answers, "category")));
    row.put("householdId", stringVal(answers, "householdId"));
    row.put("siblingAdmissionNos", answers.get("siblingAdmissionNos"));
    row.put("transport", truthy(answers.get("transport")));
    row.put("hostel", truthy(answers.get("hostel")));
    row.put("scholarship", truthy(answers.get("scholarship")));
    row.put("branchId", e.getBranchId());
    row.put("academicSessionId", e.getAcademicSessionId());
    row.put("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
    row.put("deleted", e.isDeleted());
    row.put("deletedAt", e.getDeletedAt() != null ? e.getDeletedAt().toString() : null);
    row.put("deletedBy", e.getDeletedBy());
    row.put("deleteReason", e.getDeleteReason());
    return row;
  }

  private static String exportValue(Map<String, Object> row, String key) {
    if ("photoUrl".equals(key)) {
      return StudentSheetCatalog.exportPhoto(row.get(key));
    }
    if ("classApplied".equals(key)) {
      Object value = row.get("classApplied");
      if (value == null || String.valueOf(value).isBlank()) {
        value = row.get("classSection");
      }
      return value == null ? "" : String.valueOf(value);
    }
    Object value = row.get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private static String parentName(Map<String, Object> answers) {
    Object guardians = answers.get("guardians");
    if (guardians instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
      Object name = m.get("fullName");
      if (name == null) {
        name = m.get("name");
      }
      return name != null ? String.valueOf(name) : "";
    }
    return firstNonBlank(stringVal(answers, "parentName"), stringVal(answers, "fatherName"));
  }

  private void requireFeature(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FEATURE_STUDENT_MASTER)) {
      throw new StudentException("FEATURE_OFF", "FEATURE_STUDENT_MASTER is off for this plan");
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> moduleSettings(Map<String, Object> module) {
    if (module == null) {
      return Map.of();
    }
    Object settings = module.get("settings");
    if (settings instanceof Map<?, ?> m) {
      return new LinkedHashMap<>((Map<String, Object>) m);
    }
    return new LinkedHashMap<>(module);
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

  /** true = trash only; false/null = active only. */
  private static Boolean parseIncludeDeleted(String raw) {
    if (raw == null || raw.isBlank()) {
      return Boolean.FALSE;
    }
    return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
  }

  private static String stringVal(Map<String, Object> answers, String key) {
    if (answers == null || answers.get(key) == null) {
      return "";
    }
    return String.valueOf(answers.get(key)).trim();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return "";
    }
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return "";
  }

  private static boolean truthy(Object v) {
    if (v == null) {
      return false;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    String s = String.valueOf(v).trim().toLowerCase();
    return "true".equals(s) || "yes".equals(s) || "1".equals(s);
  }

  private static String csv(Object v) {
    String s = v == null ? "" : String.valueOf(v);
    if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }

  record DirectoryQuery(
      String branch,
      String session,
      String status,
      String q,
      String classSection,
      String classLike,
      String gender,
      String category,
      String house,
      boolean transportOnly,
      boolean hostelOnly,
      boolean scholarshipOnly) {

    static DirectoryQuery from(Map<String, String> params, TenantScope scope) {
      Map<String, String> p = params != null ? params : Map.of();
      String branch = first(p.get("branchId"), scope.branchId());
      String session = first(p.get("academicSessionId"), scope.academicSessionId());
      // Dedicated identity filters reuse the global q LIKE path (PEN/APAAR/Samagra already indexed).
      String q =
          emptyToNull(
              first(
                  p.get("q"),
                  first(p.get("penNumber"), first(p.get("apaarId"), p.get("samagraId")))));
      return new DirectoryQuery(
          branch,
          session,
          emptyToNull(p.get("status")),
          q,
          emptyToNull(first(p.get("classSection"), p.get("class"))),
          classLike(p.get("classLike")),
          emptyToNull(p.get("gender")),
          emptyToNull(p.get("category")),
          emptyToNull(p.get("house")),
          truthyFlag(p.get("transport")),
          truthyFlag(p.get("hostel")),
          truthyFlag(p.get("scholarship")));
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

    /** A pattern of only wildcards would match the whole school. Require some class text. */
    private static String classLike(String raw) {
      String value = emptyToNull(raw);
      if (value == null) {
        return null;
      }
      String bare = value.replace("%", "").replace("_", "").replace("\\", "").trim();
      return bare.isEmpty() ? null : value;
    }

    private static boolean truthyFlag(String v) {
      if (v == null) {
        return false;
      }
      String s = v.trim().toLowerCase();
      return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }
  }
}
