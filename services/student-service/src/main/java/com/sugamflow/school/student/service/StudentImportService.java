package com.sugamflow.school.student.service;

import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.ImportJobEntity;
import com.sugamflow.school.student.persistence.entity.ImportJobRowEntity;
import com.sugamflow.school.student.persistence.repo.ImportJobRepository;
import com.sugamflow.school.student.persistence.repo.ImportJobRowRepository;
import com.sugamflow.school.student.sheet.StudentImportWorkbook;
import com.sugamflow.school.student.sheet.StudentSheetCatalog;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import Workbench — CSV/JSON row ingest for student_master with column mapping, dry-run, and
 * commit. Gated by FEATURE_IMPORT_WORKBENCH (falls back to FEATURE_STUDENT_MASTER when unset).
 */
@Service
public class StudentImportService {

  private static final Logger log = LoggerFactory.getLogger(StudentImportService.class);

  public static final String FEATURE_IMPORT = "FEATURE_IMPORT_WORKBENCH";
  public static final String ENTITY_STUDENT = "student_master";

  private final ImportJobRepository jobs;
  private final ImportJobRowRepository rows;
  private final StudentRecordService records;
  private final ConfigEngineClient engines;

  public StudentImportService(
      ImportJobRepository jobs,
      ImportJobRowRepository rows,
      StudentRecordService records,
      ConfigEngineClient engines) {
    this.jobs = jobs;
    this.rows = rows;
    this.records = records;
    this.engines = engines;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    boolean enabled =
        engines.isFeatureEnabled(scope, FEATURE_IMPORT)
            || engines.isFeatureEnabled(scope, StudentRecordService.FEATURE_STUDENT_MASTER);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", enabled);
    out.put("requiredFeatureFlag", FEATURE_IMPORT);
    out.put("entityTypes", List.of(ENTITY_STUDENT));
    out.put("targetFields", StudentSheetCatalog.templateKeys());
    List<Map<String, Object>> columns = new ArrayList<>();
    for (StudentSheetCatalog.Col col : StudentSheetCatalog.columns()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("key", col.key());
      item.put("label", col.label());
      item.put("required", col.required());
      columns.add(item);
    }
    out.put("columns", columns);
    out.put("modes", List.of(StudentSheetCatalog.MODE_CREATE, StudentSheetCatalog.MODE_UPDATE, StudentSheetCatalog.MODE_UPSERT));
    out.put("maxRows", StudentSheetCatalog.MAX_ROWS);
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listJobs() {
    TenantScope scope = TenantContext.require();
    requireImport(scope);
    return jobs.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId()).stream()
        .limit(50)
        .map(this::toJobDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getJob(UUID jobId) {
    TenantScope scope = TenantContext.require();
    requireImport(scope);
    ImportJobEntity job = requireJob(jobId, scope.organizationId());
    Map<String, Object> dto = toJobDto(job);
    dto.put(
        "rows",
        rows.findByJobIdOrderByRowNumberAsc(jobId).stream().limit(200).map(this::toRowDto).toList());
    dto.put(
        "errors",
        rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, "ERROR").stream()
            .limit(100)
            .map(this::toRowDto)
            .toList());
    return dto;
  }

  /**
   * Create a job from uploaded rows. Body: { fileName, entityType?, mapping?, rows: [ {col:val} ]
   * or csvText }.
   */
  @Transactional
  public Map<String, Object> createJob(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireStaff(scope);
    requireImport(scope);

    String entityType = stringOr(body.get("entityType"), ENTITY_STUDENT);
    if (!ENTITY_STUDENT.equals(entityType)) {
      throw new StudentException("VALIDATION", "Only student_master imports are supported in this slice");
    }

    Map<String, Object> mapping = asMap(body.get("mapping"));
    List<Map<String, Object>> rawRows = parseRows(body);
    if (rawRows.isEmpty()) {
      throw new StudentException("VALIDATION", "No data rows provided (rows[], csvText, or an Excel file)");
    }
    if (rawRows.size() > StudentSheetCatalog.MAX_ROWS) {
      throw new StudentException("VALIDATION", "Max 2000 rows per job");
    }
    if (mapping.isEmpty()) {
      mapping = new LinkedHashMap<>(StudentSheetCatalog.defaultMapping());
    }
    String mode = StudentSheetCatalog.normalizeMode(stringOr(body.get("mode"), null));
    List<String> unmapped = StudentSheetCatalog.unmappedHeaders(rawRows.get(0), mapping);

    ImportJobEntity job = new ImportJobEntity();
    job.setId(UUID.randomUUID());
    job.setOrganizationId(scope.organizationId());
    job.setBranchId(scope.branchId());
    job.setAcademicSessionId(scope.academicSessionId());
    job.setEntityType(entityType);
    job.setStatus("DRAFT");
    job.setFileName(stringOr(body.get("fileName"), "upload.csv"));
    job.setMappingJson(mapping);
    job.setCreatedBy(scope.userId());
    job.setCreatedAt(Instant.now());
    job.setUpdatedAt(Instant.now());

    int ok = 0;
    int err = 0;
    int skipped = 0;
    int updates = 0;
    Set<String> seenAdmission = new java.util.HashSet<>();
    List<ImportJobRowEntity> rowEntities = new ArrayList<>();
    int n = 1;
    for (Map<String, Object> raw : rawRows) {
      ImportJobRowEntity row = new ImportJobRowEntity();
      row.setId(UUID.randomUUID());
      row.setJobId(job.getId());
      row.setOrganizationId(scope.organizationId());
      row.setRowNumber(n++);
      row.setRawJson(raw);
      Map<String, Object> mapped = StudentSheetCatalog.mapRow(raw, mapping);
      StudentSheetCatalog.normalize(mapped);
      String validation =
          StudentSheetCatalog.validate(mapped, mode, scope.branchId(), scope.academicSessionId());
      if (validation == null) {
        String duplicate =
            StudentSheetCatalog.noteDuplicateAdmission(seenAdmission, stringOr(mapped.get("admissionNo"), ""));
        if (duplicate != null) {
          validation = duplicate;
        }
      }
      String outcome = null;
      if (validation == null) {
        outcome = records.previewImportOutcome(scope.organizationId(), mapped, mode);
        if ("MISSING".equals(outcome)) {
          validation = "No student matches this admission number or PEN";
        }
      }
      row.setMappedJson(mapped);
      if (validation != null) {
        row.setStatus("ERROR");
        row.setErrorMessage(validation);
        err++;
      } else if ("SKIPPED".equals(outcome)) {
        row.setStatus("SKIPPED");
        row.setErrorMessage("Admission number or PEN already exists");
        skipped++;
      } else {
        row.setStatus("READY");
        mapped.put("_action", outcome);
        row.setMappedJson(mapped);
        if ("UPDATE".equals(outcome)) {
          updates++;
        }
        ok++;
      }
      row.setCreatedAt(Instant.now());
      row.setUpdatedAt(Instant.now());
      rowEntities.add(row);
    }

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("total", rawRows.size());
    stats.put("ready", ok);
    stats.put("error", err);
    stats.put("skipped", skipped);
    stats.put("updates", updates);
    stats.put("created", Math.max(0, ok - updates));
    stats.put("committed", 0);
    stats.put("mode", mode);
    stats.put("unmapped", unmapped);
    job.setStatsJson(stats);
    job.setStatus(err > 0 && ok == 0 ? "INVALID" : "MAPPED");
    jobs.save(job);
    rows.saveAll(rowEntities);
    return getJob(job.getId());
  }

  @Transactional
  public Map<String, Object> dryRun(UUID jobId) {
    TenantScope scope = TenantContext.require();
    requireStaff(scope);
    requireImport(scope);
    ImportJobEntity job = requireJob(jobId, scope.organizationId());
    String mode = StudentSheetCatalog.normalizeMode(stringOr(job.getStatsJson().get("mode"), null));
    int ready = 0;
    int error = 0;
    int skipped = 0;
    int updates = 0;
    for (ImportJobRowEntity row : rows.findByJobIdOrderByRowNumberAsc(jobId)) {
      Map<String, Object> mapped = new LinkedHashMap<>(row.getMappedJson() == null ? Map.of() : row.getMappedJson());
      mapped.remove("_action");
      StudentSheetCatalog.normalize(mapped);
      String validation =
          StudentSheetCatalog.validate(mapped, mode, scope.branchId(), scope.academicSessionId());
      String outcome = null;
      if (validation == null) {
        outcome = records.previewImportOutcome(scope.organizationId(), mapped, mode);
        if ("MISSING".equals(outcome)) {
          validation = "No student matches this admission number or PEN";
        }
      }
      if (validation != null) {
        row.setStatus("ERROR");
        row.setErrorMessage(validation);
        error++;
      } else if ("SKIPPED".equals(outcome)) {
        row.setStatus("SKIPPED");
        row.setErrorMessage("Admission number or PEN already exists");
        skipped++;
      } else {
        row.setStatus("READY");
        row.setErrorMessage(null);
        mapped.put("_action", outcome);
        if ("UPDATE".equals(outcome)) {
          updates++;
        }
        ready++;
      }
      row.setMappedJson(mapped);
      row.setUpdatedAt(Instant.now());
      rows.save(row);
    }
    Map<String, Object> stats = new LinkedHashMap<>(job.getStatsJson());
    stats.put("ready", ready);
    stats.put("error", error);
    stats.put("skipped", skipped);
    stats.put("updates", updates);
    stats.put("created", Math.max(0, ready - updates));
    stats.put("total", ready + error + skipped);
    job.setStatsJson(stats);
    job.setStatus("VALIDATED");
    job.setUpdatedAt(Instant.now());
    jobs.save(job);
    return getJob(jobId);
  }

  public Map<String, Object> commit(UUID jobId) {
    TenantScope scope = TenantContext.require();
    requireStaff(scope);
    requireImport(scope);
    ImportJobEntity job = requireJob(jobId, scope.organizationId());
    if ("COMMITTED".equals(job.getStatus())) {
      return getJob(jobId);
    }

    int committed = 0;
    int updated = 0;
    int error = 0;
    String mode = StudentSheetCatalog.normalizeMode(stringOr(job.getStatsJson().get("mode"), null));
    long pendingErrors = rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, "ERROR").size();
    if (pendingErrors > 0) {
      throw new StudentException("VALIDATION", "Fix the listed errors before importing");
    }
    for (ImportJobRowEntity row : rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, "READY")) {
      try {
        Map<String, Object> mapped = new LinkedHashMap<>(row.getMappedJson());
        mapped.remove("_action");
        Map<String, Object> saved = records.importRow(mapped, mode);
        String outcome = stringOr(saved.get("outcome"), "CREATED");
        if ("SKIPPED".equals(outcome)) {
          row.setStatus("SKIPPED");
          row.setErrorMessage(stringOr(saved.get("message"), "Already exists"));
        } else {
          row.setStatus("UPDATED".equals(outcome) ? "UPDATED" : "COMMITTED");
          row.setErrorMessage(null);
          if ("UPDATED".equals(outcome)) {
            updated++;
          }
          committed++;
        }
        mapped.put("_studentId", saved.get("id"));
        mapped.put("_action", outcome);
        row.setMappedJson(mapped);
      } catch (Exception ex) {
        log.warn("Import row {} on job {} was not saved", row.getRowNumber(), jobId, ex);
        row.setStatus("ERROR");
        row.setErrorMessage(rowError(ex));
        error++;
      }
      row.setUpdatedAt(Instant.now());
      rows.save(row);
    }

    Map<String, Object> stats = new LinkedHashMap<>(job.getStatsJson());
    stats.put("committed", committed);
    stats.put("updatedCommitted", updated);
    stats.put("error", ((Number) stats.getOrDefault("error", 0)).intValue() + error);
    stats.put("ready", 0);
    job.setStatsJson(stats);
    job.setStatus(error > 0 && committed == 0 ? "FAILED" : "COMMITTED");
    job.setUpdatedAt(Instant.now());
    jobs.save(job);
    return getJob(jobId);
  }

  public byte[] template() {
    TenantScope scope = TenantContext.require();
    requireImport(scope);
    return StudentImportWorkbook.template();
  }

  public byte[] errorReport(UUID jobId) {
    TenantScope scope = TenantContext.require();
    requireImport(scope);
    requireJob(jobId, scope.organizationId());
    List<Map<String, Object>> errors = new ArrayList<>();
    for (ImportJobRowEntity row : rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, "ERROR")) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("rowNumber", row.getRowNumber());
      item.put("errorMessage", row.getErrorMessage());
      Map<String, Object> mapped = row.getMappedJson() == null ? Map.of() : row.getMappedJson();
      item.put("admissionNo", mapped.get("admissionNo"));
      item.put("fullName", mapped.get("fullName"));
      errors.add(item);
    }
    return StudentImportWorkbook.errorReport(errors);
  }

  private void requireImport(TenantScope scope) {
    boolean enabled =
        engines.isFeatureEnabled(scope, FEATURE_IMPORT)
            || engines.isFeatureEnabled(scope, StudentRecordService.FEATURE_STUDENT_MASTER);
    if (!enabled) {
      throw new StudentException(
          "FEATURE_DISABLED", "FEATURE_IMPORT_WORKBENCH is off for this subscription plan.");
    }
  }

  private void requireStaff(TenantScope scope) {
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
  }

  private ImportJobEntity requireJob(UUID id, String org) {
    return jobs.findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new StudentException("NOT_FOUND", "Import job not found"));
  }

  private static Map<String, Object> defaultStudentMapping() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("fullName", "fullName");
    m.put("admissionNo", "admissionNo");
    m.put("age", "age");
    m.put("mobile", "mobile");
    m.put("email", "email");
    m.put("classApplied", "classApplied");
    m.put("gender", "gender");
    m.put("rollNo", "rollNo");
    return m;
  }

  private static Map<String, Object> applyMapping(
      Map<String, Object> raw, Map<String, Object> mapping) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : mapping.entrySet()) {
      String target = e.getKey();
      String source = String.valueOf(e.getValue());
      Object val = raw.get(source);
      if (val == null) {
        // try case-insensitive header match
        for (Map.Entry<String, Object> r : raw.entrySet()) {
          if (r.getKey() != null && r.getKey().equalsIgnoreCase(source)) {
            val = r.getValue();
            break;
          }
        }
      }
      if (val != null && !String.valueOf(val).isBlank()) {
        mapped.put(target, val);
      }
    }
    return mapped;
  }

  private static String validateMapped(Map<String, Object> mapped) {
    if (mapped == null || mapped.isEmpty()) {
      return "Empty mapped row";
    }
    if (isBlank(mapped.get("fullName"))) {
      return "fullName is required";
    }
    if (isBlank(mapped.get("mobile"))) {
      return "mobile is required";
    }
    if (isBlank(mapped.get("classApplied"))) {
      return "classApplied is required";
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parseRows(Map<String, Object> body) {
    if (body.get("rows") instanceof List<?> list) {
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          out.add(new LinkedHashMap<>((Map<String, Object>) m));
        }
      }
      return out;
    }
    String csv = stringOr(body.get("csvText"), null);
    if (csv != null && !csv.isBlank()) {
      return parseCsv(csv);
    }
    String file = stringOr(body.get("fileBase64"), null);
    if (file != null && !file.isBlank()) {
      try {
        return StudentImportWorkbook.parse(StudentImportWorkbook.decodeBase64(file));
      } catch (IllegalArgumentException ex) {
        throw new StudentException("VALIDATION", "Upload a .csv or .xlsx file");
      }
    }
    return List.of();
  }

  private static List<Map<String, Object>> parseCsv(String csv) {
    String[] lines = csv.replace("\r\n", "\n").replace('\r', '\n').split("\n");
    if (lines.length < 2) {
      return List.of();
    }
    String[] headers = splitCsvLine(lines[0]);
    List<Map<String, Object>> out = new ArrayList<>();
    for (int i = 1; i < lines.length; i++) {
      if (lines[i] == null || lines[i].isBlank()) {
        continue;
      }
      String[] cols = splitCsvLine(lines[i]);
      Map<String, Object> row = new LinkedHashMap<>();
      for (int c = 0; c < headers.length; c++) {
        String h = headers[c].trim();
        if (h.isEmpty()) {
          continue;
        }
        String v = c < cols.length ? cols[c].trim() : "";
        row.put(h, v);
      }
      out.add(row);
    }
    return out;
  }

  /** Minimal CSV splitter (supports quoted commas). */
  private static String[] splitCsvLine(String line) {
    List<String> parts = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        inQuotes = !inQuotes;
      } else if (ch == ',' && !inQuotes) {
        parts.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(ch);
      }
    }
    parts.add(cur.toString());
    return parts.toArray(String[]::new);
  }

  private Map<String, Object> toJobDto(ImportJobEntity j) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", j.getId().toString());
    m.put("entityType", j.getEntityType());
    m.put("status", j.getStatus());
    m.put("fileName", j.getFileName());
    m.put("mapping", j.getMappingJson());
    m.put("stats", j.getStatsJson());
    m.put("createdBy", j.getCreatedBy());
    m.put("createdAt", j.getCreatedAt().toString());
    m.put("updatedAt", j.getUpdatedAt().toString());
    Object mode = j.getStatsJson() == null ? null : j.getStatsJson().get("mode");
    m.put("mode", mode == null ? StudentSheetCatalog.MODE_CREATE : mode);
    return m;
  }

  private Map<String, Object> toRowDto(ImportJobRowEntity r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId().toString());
    m.put("rowNumber", r.getRowNumber());
    m.put("status", r.getStatus());
    m.put("raw", r.getRawJson());
    m.put("mapped", r.getMappedJson());
    m.put("errorMessage", r.getErrorMessage());
    return m;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object v) {
    if (v instanceof Map<?, ?> m) {
      return new LinkedHashMap<>((Map<String, Object>) m);
    }
    return new LinkedHashMap<>();
  }

  private static String rowError(Exception ex) {
    Throwable root = ex;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String message = root.getMessage();
    if (message == null || message.isBlank()) {
      message = ex.getMessage();
    }
    if (message == null || message.isBlank()) {
      return "commit failed";
    }
    return message.length() > 400 ? message.substring(0, 400) : message;
  }

  private static boolean isBlank(Object v) {
    return v == null || String.valueOf(v).trim().isEmpty();
  }

  private static String stringOr(Object v, String fallback) {
    if (v == null) {
      return fallback;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? fallback : s;
  }
}
