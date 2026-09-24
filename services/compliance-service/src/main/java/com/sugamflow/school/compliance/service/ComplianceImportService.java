package com.sugamflow.school.compliance.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.dto.ImportBootstrapResponse;
import com.sugamflow.school.compliance.dto.ImportCommitResponse;
import com.sugamflow.school.compliance.dto.ImportJobResponse;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.integration.MasterDataClient;
import com.sugamflow.school.compliance.persistence.entity.ComplianceFieldMapEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceImportJobEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceImportJobRowEntity;
import com.sugamflow.school.compliance.persistence.entity.SchoolComplianceProfileEntity;
import com.sugamflow.school.compliance.persistence.repo.ComplianceFieldMapRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceImportJobRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceImportJobRowRepository;
import com.sugamflow.school.compliance.persistence.repo.SchoolComplianceProfileRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class ComplianceImportService {
  private static final Set<String> MATCH_KEYS = Set.of("admissionNo", "employeeNo");
  private static final int MAX_ROWS = 5000;
  private static final int MAX_DETAIL_ROWS = 500;

  private final ComplianceImportJobRepository jobRepository;
  private final ComplianceImportJobRowRepository rowRepository;
  private final ComplianceFieldMapRepository fieldMapRepository;
  private final SchoolComplianceProfileRepository profileRepository;
  private final MasterDataClient masterDataClient;
  private final ConfigEngineClient configEngineClient;
  private final BoardPackService boardPackService;

  public ComplianceImportService(
      ComplianceImportJobRepository jobRepository,
      ComplianceImportJobRowRepository rowRepository,
      ComplianceFieldMapRepository fieldMapRepository,
      SchoolComplianceProfileRepository profileRepository,
      MasterDataClient masterDataClient,
      ConfigEngineClient configEngineClient,
      BoardPackService boardPackService) {
    this.jobRepository = jobRepository;
    this.rowRepository = rowRepository;
    this.fieldMapRepository = fieldMapRepository;
    this.profileRepository = profileRepository;
    this.masterDataClient = masterDataClient;
    this.configEngineClient = configEngineClient;
    this.boardPackService = boardPackService;
  }

  @Transactional(readOnly = true)
  public ImportBootstrapResponse bootstrap() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ProfileBoard board = resolveBoard(scope);
    return new ImportBootstrapResponse(
        board.boardCode(),
        board.packKey(),
        "admissionNo",
        "employeeNo",
        true,
        columnsFor(board.boardCode(), "STUDENT"),
        columnsFor(board.boardCode(), "STAFF"));
  }

  @Transactional(readOnly = true)
  public byte[] templateCsv(String entityType) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    String type = normalizeEntityType(entityType);
    ProfileBoard board = resolveBoard(scope);
    List<ComplianceFieldMapEntity> maps = activeMaps(board.boardCode(), type);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < maps.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(escapeCsv(maps.get(i).getFieldKey()));
    }
    sb.append('\n');
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Transactional(readOnly = true)
  public List<ImportJobResponse> listJobs() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    return jobRepository.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId()).stream()
        .map(ImportJobResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public ImportJobResponse getJob(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceImportJobEntity job = requireJob(id, scope.organizationId());
    List<ComplianceImportJobRowEntity> rows =
        rowRepository.findByJobIdOrderByRowNoAsc(job.getId());
    if (rows.size() > MAX_DETAIL_ROWS) {
      rows = rows.subList(0, MAX_DETAIL_ROWS);
    }
    return ImportJobResponse.from(job, rows);
  }

  @Transactional
  public ImportJobResponse upload(MultipartFile file, String entityType, Boolean fillBlankOnly)
      throws IOException {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    if (file == null || file.isEmpty()) {
      throw new ComplianceException("IMPORT_EMPTY", "Upload a CSV or XLSX file.", HttpStatus.BAD_REQUEST);
    }
    String type = normalizeEntityType(entityType);
    ProfileBoard board = resolveBoard(scope);
    List<ComplianceFieldMapEntity> maps = activeMaps(board.boardCode(), type);
    if (maps.isEmpty()) {
      throw new ComplianceException(
          "IMPORT_NO_FIELDS",
          "No active field map for " + board.boardCode() + " / " + type,
          HttpStatus.BAD_REQUEST);
    }

    String fileName = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
    List<Map<String, String>> parsed = parseFile(file.getBytes(), fileName, maps);
    return createAndValidate(scope, board, type, fileName, fillBlankOnly == null || fillBlankOnly, parsed, maps);
  }

  @Transactional
  public ImportJobResponse validate(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceImportJobEntity job = requireJob(id, scope.organizationId());
    if ("COMMITTED".equalsIgnoreCase(job.getStatus())) {
      throw new ComplianceException(
          "IMPORT_COMMITTED", "Job already committed.", HttpStatus.CONFLICT);
    }
    List<ComplianceFieldMapEntity> maps = activeMaps(job.getBoardCode(), job.getEntityType());
    List<ComplianceImportJobRowEntity> rows = rowRepository.findByJobIdOrderByRowNoAsc(job.getId());
    applyValidation(scope, job, rows, maps);
    jobRepository.save(job);
    rowRepository.saveAll(rows);
    return ImportJobResponse.from(job, limitRows(rows));
  }

  @Transactional
  public ImportCommitResponse commit(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    ComplianceImportJobEntity job = requireJob(id, scope.organizationId());
    if ("COMMITTED".equalsIgnoreCase(job.getStatus())) {
      throw new ComplianceException(
          "IMPORT_COMMITTED", "Job already committed.", HttpStatus.CONFLICT);
    }
    List<ComplianceFieldMapEntity> maps = activeMaps(job.getBoardCode(), job.getEntityType());
    List<ComplianceImportJobRowEntity> rows = rowRepository.findByJobIdOrderByRowNoAsc(job.getId());
    applyValidation(scope, job, rows, maps);

    Map<String, Map<String, Object>> masters = indexMasters(scope, job.getEntityType());
    int updated = 0;
    for (ComplianceImportJobRowEntity row : rows) {
      if (!"READY".equalsIgnoreCase(row.getStatus())) {
        continue;
      }
      Map<String, Object> master = masters.get(normKey(row.getMatchKey()));
      if (master == null) {
        row.setStatus("ERROR");
        row.setErrorMessage("Master record no longer found for match key.");
        continue;
      }
      Map<String, Object> patch = buildAnswersPatch(row.getPayloadJson(), master, maps, job.isFillBlankOnly());
      if (patch.isEmpty()) {
        row.setStatus("SKIPPED");
        row.setErrorMessage("No blank fields to fill.");
        continue;
      }
      try {
        String entityId = str(master.get("id"));
        if ("STUDENT".equals(job.getEntityType())) {
          masterDataClient.patchStudentAnswers(scope, entityId, patch);
        } else {
          masterDataClient.patchStaffAnswers(scope, entityId, patch);
        }
        row.setEntityId(entityId);
        row.setStatus("UPDATED");
        row.setErrorMessage(null);
        updated++;
      } catch (Exception ex) {
        row.setStatus("ERROR");
        row.setErrorMessage(ex.getMessage());
      }
    }

    job.setUpdatedCount(updated);
    recount(job, rows);
    job.setStatus("COMMITTED");
    jobRepository.save(job);
    rowRepository.saveAll(rows);

    String hint =
        updated > 0
            ? "Re-run Data Readiness validation to refresh findings."
            : "No rows updated. Fix errors or upload fields that are blank on masters.";
    return new ImportCommitResponse(ImportJobResponse.from(job, limitRows(rows)), hint);
  }

  private ImportJobResponse createAndValidate(
      TenantScope scope,
      ProfileBoard board,
      String entityType,
      String fileName,
      boolean fillBlankOnly,
      List<Map<String, String>> parsed,
      List<ComplianceFieldMapEntity> maps) {
    ComplianceImportJobEntity job = new ComplianceImportJobEntity();
    job.setOrganizationId(scope.organizationId());
    job.setBoardCode(board.boardCode());
    job.setPackKey(board.packKey());
    job.setEntityType(entityType);
    job.setFileName(fileName);
    job.setFillBlankOnly(fillBlankOnly);
    job.setStatus("DRAFT");
    job.setCreatedBy(scope.userId());
    job = jobRepository.save(job);

    List<ComplianceImportJobRowEntity> rows = new ArrayList<>();
    int rowNo = 1;
    for (Map<String, String> cells : parsed) {
      ComplianceImportJobRowEntity row = new ComplianceImportJobRowEntity();
      row.setJobId(job.getId());
      row.setRowNo(rowNo++);
      Map<String, Object> payload = new LinkedHashMap<>();
      for (Map.Entry<String, String> e : cells.entrySet()) {
        if (e.getValue() != null && !e.getValue().isBlank()) {
          payload.put(e.getKey(), e.getValue().trim());
        }
      }
      row.setPayloadJson(payload);
      String matchField = matchField(entityType);
      row.setMatchKey(str(payload.get(matchField)));
      rows.add(row);
    }
    job.setTotalRows(rows.size());
    applyValidation(scope, job, rows, maps);
    rowRepository.saveAll(rows);
    jobRepository.save(job);
    return ImportJobResponse.from(job, limitRows(rows));
  }

  private void applyValidation(
      TenantScope scope,
      ComplianceImportJobEntity job,
      List<ComplianceImportJobRowEntity> rows,
      List<ComplianceFieldMapEntity> maps) {
    Map<String, ComplianceFieldMapEntity> byKey =
        maps.stream()
            .collect(
                Collectors.toMap(
                    ComplianceFieldMapEntity::getFieldKey, m -> m, (a, b) -> a, LinkedHashMap::new));
    Map<String, Map<String, Object>> masters = indexMasters(scope, job.getEntityType());
    String matchField = matchField(job.getEntityType());

    for (ComplianceImportJobRowEntity row : rows) {
      if ("UPDATED".equalsIgnoreCase(row.getStatus()) || "SKIPPED".equalsIgnoreCase(row.getStatus())) {
        continue;
      }
      Map<String, Object> payload = new LinkedHashMap<>(row.getPayloadJson());
      String matchKey = str(payload.get(matchField));
      row.setMatchKey(matchKey.isEmpty() ? null : matchKey);

      if (matchKey.isEmpty()) {
        row.setStatus("ERROR");
        row.setEntityId(null);
        row.setErrorMessage("Missing " + matchField + " (required to match existing master).");
        continue;
      }

      List<String> errors = new ArrayList<>();
      for (Map.Entry<String, Object> e : payload.entrySet()) {
        ComplianceFieldMapEntity map = byKey.get(e.getKey());
        if (map == null) {
          continue;
        }
        String value = str(e.getValue());
        if (value.isEmpty()) {
          continue;
        }
        if (map.getFormatRegex() != null && !map.getFormatRegex().isBlank()) {
          try {
            if (!Pattern.compile(map.getFormatRegex()).matcher(value).matches()) {
              errors.add(map.getLabel() + " format invalid");
            }
          } catch (Exception ignored) {
            // bad regex in pack — skip format check
          }
        }
      }

      Map<String, Object> master = masters.get(normKey(matchKey));
      if (master == null) {
        row.setStatus("ERROR");
        row.setEntityId(null);
        row.setErrorMessage("No " + job.getEntityType().toLowerCase(Locale.ROOT) + " matched " + matchField + "=" + matchKey);
        continue;
      }
      row.setEntityId(str(master.get("id")));

      Map<String, Object> wouldPatch =
          buildAnswersPatch(payload, master, maps, job.isFillBlankOnly());
      if (wouldPatch.isEmpty() && errors.isEmpty()) {
        row.setStatus("SKIPPED");
        row.setErrorMessage(
            job.isFillBlankOnly()
                ? "All mapped fields already filled on master (blank-only mode)."
                : "No updatable fields in row.");
        continue;
      }
      if (!errors.isEmpty()) {
        row.setStatus("ERROR");
        row.setErrorMessage(String.join("; ", errors));
        continue;
      }
      row.setStatus("READY");
      row.setErrorMessage(null);
    }

    recount(job, rows);
    job.setStatus(job.getErrorCount() > 0 && job.getReadyCount() == 0 ? "DRAFT" : "VALIDATED");
  }

  private Map<String, Object> buildAnswersPatch(
      Map<String, Object> payload,
      Map<String, Object> master,
      List<ComplianceFieldMapEntity> maps,
      boolean fillBlankOnly) {
    Map<String, Object> patch = new LinkedHashMap<>();
    Set<String> allowed =
        maps.stream().map(ComplianceFieldMapEntity::getFieldKey).collect(Collectors.toSet());
    for (Map.Entry<String, Object> e : payload.entrySet()) {
      String key = e.getKey();
      if (MATCH_KEYS.contains(key) || !allowed.contains(key)) {
        continue;
      }
      String incoming = str(e.getValue());
      if (incoming.isEmpty()) {
        continue;
      }
      if (fillBlankOnly && !blank(master.get(key))) {
        continue;
      }
      patch.put(key, incoming);
    }
    return patch;
  }

  private Map<String, Map<String, Object>> indexMasters(TenantScope scope, String entityType) {
    List<Map<String, Object>> list =
        "STUDENT".equals(entityType)
            ? masterDataClient.listStudentProjections(scope)
            : masterDataClient.listStaffProjections(scope);
    String matchField = matchField(entityType);
    Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
    for (Map<String, Object> row : list) {
      String key = normKey(str(row.get(matchField)));
      if (!key.isEmpty()) {
        byKey.putIfAbsent(key, row);
      }
    }
    return byKey;
  }

  private List<Map<String, String>> parseFile(
      byte[] bytes, String fileName, List<ComplianceFieldMapEntity> maps) throws IOException {
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
      return parseExcel(bytes, maps);
    }
    return parseCsv(bytes, maps);
  }

  private List<Map<String, String>> parseCsv(byte[] bytes, List<ComplianceFieldMapEntity> maps)
      throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
      String headerLine = reader.readLine();
      if (headerLine == null || headerLine.isBlank()) {
        throw new ComplianceException("IMPORT_EMPTY", "CSV has no header row.", HttpStatus.BAD_REQUEST);
      }
      if (headerLine.startsWith("\uFEFF")) {
        headerLine = headerLine.substring(1);
      }
      List<String> headers = parseCsvLine(headerLine);
      List<String> fieldKeys = resolveHeaders(headers, maps);
      List<Map<String, String>> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        List<String> cells = parseCsvLine(line);
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < fieldKeys.size(); i++) {
          String key = fieldKeys.get(i);
          if (key == null) {
            continue;
          }
          String val = i < cells.size() ? cells.get(i) : "";
          row.put(key, val == null ? "" : val.trim());
        }
        if (row.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
          rows.add(row);
        }
        if (rows.size() > MAX_ROWS) {
          throw new ComplianceException(
              "IMPORT_TOO_LARGE", "Max " + MAX_ROWS + " data rows per import.", HttpStatus.BAD_REQUEST);
        }
      }
      return rows;
    }
  }

  private List<Map<String, String>> parseExcel(byte[] bytes, List<ComplianceFieldMapEntity> maps)
      throws IOException {
    DataFormatter formatter = new DataFormatter();
    try (InputStream in = new ByteArrayInputStream(bytes);
        Workbook workbook = WorkbookFactory.create(in)) {
      Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
      if (sheet == null) {
        throw new ComplianceException("IMPORT_EMPTY", "Workbook has no sheets.", HttpStatus.BAD_REQUEST);
      }
      Row headerRow = sheet.getRow(sheet.getFirstRowNum());
      if (headerRow == null) {
        throw new ComplianceException("IMPORT_EMPTY", "Excel has no header row.", HttpStatus.BAD_REQUEST);
      }
      List<String> headers = new ArrayList<>();
      short last = headerRow.getLastCellNum();
      for (int i = 0; i < last; i++) {
        Cell cell = headerRow.getCell(i);
        headers.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
      }
      List<String> fieldKeys = resolveHeaders(headers, maps);
      List<Map<String, String>> rows = new ArrayList<>();
      for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
        Row excelRow = sheet.getRow(r);
        if (excelRow == null) {
          continue;
        }
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < fieldKeys.size(); i++) {
          String key = fieldKeys.get(i);
          if (key == null) {
            continue;
          }
          Cell cell = excelRow.getCell(i);
          String val = cell == null ? "" : formatter.formatCellValue(cell).trim();
          row.put(key, val);
        }
        if (row.values().stream().anyMatch(v -> v != null && !v.isBlank())) {
          rows.add(row);
        }
        if (rows.size() > MAX_ROWS) {
          throw new ComplianceException(
              "IMPORT_TOO_LARGE", "Max " + MAX_ROWS + " data rows per import.", HttpStatus.BAD_REQUEST);
        }
      }
      return rows;
    }
  }

  private List<String> resolveHeaders(List<String> headers, List<ComplianceFieldMapEntity> maps) {
    Map<String, String> alias = new LinkedHashMap<>();
    for (ComplianceFieldMapEntity m : maps) {
      alias.put(normHeader(m.getFieldKey()), m.getFieldKey());
      alias.put(normHeader(m.getLabel()), m.getFieldKey());
      alias.put(normHeader(m.getSourcePath()), m.getFieldKey());
    }
    List<String> keys = new ArrayList<>();
    int mapped = 0;
    for (String h : headers) {
      String key = alias.get(normHeader(h));
      keys.add(key);
      if (key != null) {
        mapped++;
      }
    }
    if (mapped == 0) {
      throw new ComplianceException(
          "IMPORT_HEADERS",
          "No columns matched the board field map. Use the downloadable template.",
          HttpStatus.BAD_REQUEST);
    }
    return keys;
  }

  private static List<String> parseCsvLine(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            cur.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          cur.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
      } else if (c == ',') {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    out.add(cur.toString());
    return out;
  }

  private static String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return '"' + value.replace("\"", "\"\"") + '"';
    }
    return value;
  }

  private List<ImportBootstrapResponse.ImportColumn> columnsFor(String boardCode, String entityType) {
    return activeMaps(boardCode, entityType).stream()
        .map(
            m ->
                new ImportBootstrapResponse.ImportColumn(
                    m.getFieldKey(),
                    m.getLabel(),
                    m.isRequired(),
                    m.getSeverity(),
                    m.getFormatRegex()))
        .toList();
  }

  private List<ComplianceFieldMapEntity> activeMaps(String boardCode, String entityType) {
    return fieldMapRepository.findByBoardCodeAndEntityTypeAndActiveTrueOrderBySortOrderAsc(
        boardCode, entityType);
  }

  private ProfileBoard resolveBoard(TenantScope scope) {
    SchoolComplianceProfileEntity profile =
        profileRepository.findByOrganizationId(scope.organizationId()).orElse(null);
    String board =
        profile != null && profile.getBoardCode() != null && !profile.getBoardCode().isBlank()
            ? profile.getBoardCode().trim().toUpperCase(Locale.ROOT)
            : "CBSE";
    String pack =
        profile != null && profile.getActivePackKey() != null
            ? profile.getActivePackKey()
            : boardPackService.defaultPackKey(board);
    return new ProfileBoard(board, pack);
  }

  private ComplianceImportJobEntity requireJob(Long id, String organizationId) {
    return jobRepository
        .findById(id)
        .filter(j -> organizationId.equals(j.getOrganizationId()))
        .orElseThrow(
            () ->
                new ComplianceException(
                    "IMPORT_NOT_FOUND", "Import job not found.", HttpStatus.NOT_FOUND));
  }

  private void recount(ComplianceImportJobEntity job, List<ComplianceImportJobRowEntity> rows) {
    int ready = 0;
    int error = 0;
    int matched = 0;
    for (ComplianceImportJobRowEntity row : rows) {
      if (row.getEntityId() != null && !row.getEntityId().isBlank()) {
        matched++;
      }
      if ("READY".equalsIgnoreCase(row.getStatus())) {
        ready++;
      }
      if ("ERROR".equalsIgnoreCase(row.getStatus())) {
        error++;
      }
    }
    job.setReadyCount(ready);
    job.setErrorCount(error);
    job.setMatchedCount(matched);
    job.setTotalRows(rows.size());
  }

  private List<ComplianceImportJobRowEntity> limitRows(List<ComplianceImportJobRowEntity> rows) {
    if (rows.size() <= MAX_DETAIL_ROWS) {
      return rows;
    }
    return rows.subList(0, MAX_DETAIL_ROWS);
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private static String normalizeEntityType(String entityType) {
    String t = entityType == null ? "STUDENT" : entityType.trim().toUpperCase(Locale.ROOT);
    if (!"STUDENT".equals(t) && !"STAFF".equals(t)) {
      throw new ComplianceException(
          "IMPORT_ENTITY", "entityType must be STUDENT or STAFF.", HttpStatus.BAD_REQUEST);
    }
    return t;
  }

  private static String matchField(String entityType) {
    return "STAFF".equals(entityType) ? "employeeNo" : "admissionNo";
  }

  private static String normKey(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static String normHeader(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
  }

  private static boolean blank(Object value) {
    return value == null || str(value).isBlank();
  }

  private static String str(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private record ProfileBoard(String boardCode, String packKey) {}
}
