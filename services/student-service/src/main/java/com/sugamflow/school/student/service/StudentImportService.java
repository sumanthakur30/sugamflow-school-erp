package com.sugamflow.school.student.service;

import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.ImportJobEntity;
import com.sugamflow.school.student.persistence.entity.ImportJobRowEntity;
import com.sugamflow.school.student.persistence.repo.ImportJobRepository;
import com.sugamflow.school.student.persistence.repo.ImportJobRowRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import Workbench — CSV/JSON row ingest for student_master with column mapping, dry-run, and
 * commit. Gated by FEATURE_IMPORT_WORKBENCH (falls back to FEATURE_STUDENT_MASTER when unset).
 */
@Service
public class StudentImportService {

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
    out.put(
        "targetFields",
        List.of("fullName", "admissionNo", "age", "mobile", "email", "classApplied", "gender", "rollNo"));
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
    if (mapping.isEmpty()) {
      mapping = defaultStudentMapping();
    }

    List<Map<String, Object>> rawRows = parseRows(body);
    if (rawRows.isEmpty()) {
      throw new StudentException("VALIDATION", "No data rows provided (rows[] or csvText)");
    }
    if (rawRows.size() > 2000) {
      throw new StudentException("VALIDATION", "Max 2000 rows per job");
    }

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
    List<ImportJobRowEntity> rowEntities = new ArrayList<>();
    int n = 1;
    for (Map<String, Object> raw : rawRows) {
      ImportJobRowEntity row = new ImportJobRowEntity();
      row.setId(UUID.randomUUID());
      row.setJobId(job.getId());
      row.setOrganizationId(scope.organizationId());
      row.setRowNumber(n++);
      row.setRawJson(raw);
      Map<String, Object> mapped = applyMapping(raw, mapping);
      row.setMappedJson(mapped);
      String validation = validateMapped(mapped);
      if (validation != null) {
        row.setStatus("ERROR");
        row.setErrorMessage(validation);
        err++;
      } else {
        row.setStatus("READY");
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
    stats.put("committed", 0);
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
    int ready = 0;
    int error = 0;
    for (ImportJobRowEntity row : rows.findByJobIdOrderByRowNumberAsc(jobId)) {
      String validation = validateMapped(row.getMappedJson());
      if (validation != null) {
        row.setStatus("ERROR");
        row.setErrorMessage(validation);
        error++;
      } else {
        row.setStatus("READY");
        row.setErrorMessage(null);
        ready++;
      }
      row.setUpdatedAt(Instant.now());
      rows.save(row);
    }
    Map<String, Object> stats = new LinkedHashMap<>(job.getStatsJson());
    stats.put("ready", ready);
    stats.put("error", error);
    stats.put("total", ready + error);
    job.setStatsJson(stats);
    job.setStatus("VALIDATED");
    job.setUpdatedAt(Instant.now());
    jobs.save(job);
    return getJob(jobId);
  }

  @Transactional
  public Map<String, Object> commit(UUID jobId) {
    TenantScope scope = TenantContext.require();
    requireStaff(scope);
    requireImport(scope);
    ImportJobEntity job = requireJob(jobId, scope.organizationId());
    if ("COMMITTED".equals(job.getStatus())) {
      return getJob(jobId);
    }

    int committed = 0;
    int error = 0;
    for (ImportJobRowEntity row : rows.findByJobIdAndStatusOrderByRowNumberAsc(jobId, "READY")) {
      try {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answers", row.getMappedJson());
        Map<String, Object> created = records.createFromImport(body);
        row.setStatus("COMMITTED");
        row.setErrorMessage(null);
        Map<String, Object> mapped = new LinkedHashMap<>(row.getMappedJson());
        mapped.put("_studentId", created.get("id"));
        row.setMappedJson(mapped);
        committed++;
      } catch (Exception ex) {
        row.setStatus("ERROR");
        row.setErrorMessage(ex.getMessage() == null ? "commit failed" : ex.getMessage());
        error++;
      }
      row.setUpdatedAt(Instant.now());
      rows.save(row);
    }

    Map<String, Object> stats = new LinkedHashMap<>(job.getStatsJson());
    stats.put("committed", committed);
    stats.put("error", ((Number) stats.getOrDefault("error", 0)).intValue() + error);
    stats.put("ready", 0);
    job.setStatsJson(stats);
    job.setStatus(committed > 0 ? "COMMITTED" : "FAILED");
    job.setUpdatedAt(Instant.now());
    jobs.save(job);
    return getJob(jobId);
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
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return parseCsv(csv);
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
