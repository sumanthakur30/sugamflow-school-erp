package com.sugamflow.school.exam.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.persistence.entity.ClassroomItemEntity;
import com.sugamflow.school.exam.persistence.entity.ClassroomResponseEntity;
import com.sugamflow.school.exam.persistence.repo.ClassroomItemRepository;
import com.sugamflow.school.exam.persistence.repo.ClassroomResponseRepository;
import com.sugamflow.school.exam.web.ExamException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService {

  private static final Set<String> KINDS =
      Set.of(
          "STUDY_MATERIAL",
          "QUESTION",
          "QUIZ",
          "LESSON_PLAN",
          "CALENDAR",
          "GALLERY",
          "OFFLINE_TEST");

  private final ClassroomItemRepository items;
  private final ClassroomResponseRepository responses;

  public ClassroomService(ClassroomItemRepository items, ClassroomResponseRepository responses) {
    this.items = items;
    this.responses = responses;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String kind) {
    TenantScope scope = TenantContext.require();
    String normalized = normalizeKind(kind);
    return items
        .findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), normalized)
        .stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> create(String kind, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String normalized = normalizeKind(kind);
    ClassroomItemEntity entity = new ClassroomItemEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(blankToNull(scope.branchId()));
    entity.setAcademicSessionId(blankToNull(scope.academicSessionId()));
    entity.setKind(normalized);
    entity.setStatus(text(body.get("status"), "PUBLISHED").toUpperCase(Locale.ROOT));
    entity.setTitle(text(body.get("title"), normalized.replace('_', ' ')));
    entity.setNote(text(body.get("note"), null));
    entity.setPayload(payloadOf(body.get("payload")));
    entity.setCreatedBy(scope.userId());
    Instant now = Instant.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return toDto(items.save(entity));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listResponses(UUID itemId) {
    TenantScope scope = TenantContext.require();
    requireItem(itemId, scope.organizationId());
    return responses
        .findByOrganizationIdAndItemIdOrderByCreatedAtDesc(scope.organizationId(), itemId)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public Map<String, Object> respond(UUID itemId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireItem(itemId, scope.organizationId());
    return toResponse(saveResponse(scope, itemId, body));
  }

  @Transactional
  public Map<String, Object> generatePaper(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    List<Map<String, Object>> questions =
        items.findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), "QUESTION")
            .stream()
            .map(this::toDto)
            .toList();
    Object rawIds = body.get("questionIds");
    if (rawIds instanceof List<?> ids && !ids.isEmpty()) {
      List<String> wanted = ids.stream().map(String::valueOf).toList();
      questions = questions.stream().filter(q -> wanted.contains(String.valueOf(q.get("id")))).toList();
    }
    if (questions.isEmpty()) {
      throw new ExamException("VALIDATION", "Add question-bank items before generating a paper");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("questions", questions);
    Map<String, Object> paper = new LinkedHashMap<>();
    paper.put("title", text(body.get("title"), "Question paper"));
    paper.put("note", text(body.get("note"), questions.size() + " questions"));
    paper.put("status", "PUBLISHED");
    paper.put("payload", payload);
    return create("QUIZ", paper);
  }

  @Transactional
  public Map<String, Object> importWorkbook(UUID itemId, InputStream input) {
    StringBuilder csv = new StringBuilder();
    DataFormatter formatter = new DataFormatter();
    try (Workbook workbook = WorkbookFactory.create(input)) {
      Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
      if (sheet == null) {
        throw new ExamException("VALIDATION", "Workbook has no sheet");
      }
      for (Row row : sheet) {
        if (row == null) {
          continue;
        }
        String first = formatter.formatCellValue(row.getCell(0)).trim();
        short last = row.getLastCellNum();
        Cell marksCell = last > 1 ? row.getCell(last - 1) : row.getCell(1);
        String marks = formatter.formatCellValue(marksCell).trim();
        if (!first.isEmpty()) {
          csv.append(first).append(',').append(marks).append('\n');
        }
      }
    } catch (IOException ex) {
      throw new ExamException("VALIDATION", "Could not read the workbook");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("csv", csv.toString());
    return importMarks(itemId, body);
  }

  /** Paste from Excel saved as CSV: admissionNo,marks or admissionNo,name,marks. */
  @Transactional
  public Map<String, Object> importMarks(UUID itemId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireItem(itemId, scope.organizationId());
    String csv = text(body.get("csv"), "");
    if (csv.isBlank()) {
      throw new ExamException("VALIDATION", "csv is required");
    }
    int saved = 0;
    List<String> skipped = new ArrayList<>();
    for (String rawLine : csv.split("\\R")) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.toLowerCase(Locale.ROOT).startsWith("admission")) {
        continue;
      }
      String[] parts = line.split("[,;\\t]");
      if (parts.length < 2) {
        skipped.add(line);
        continue;
      }
      String admissionNo = parts[0].trim();
      String name = parts.length > 2 ? parts[1].trim() : null;
      String marks = parts[parts.length - 1].trim();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("admissionNo", admissionNo);
      row.put("studentName", name);
      row.put("score", marks);
      row.put("status", "MARKED");
      saveResponse(scope, itemId, row);
      saved++;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("saved", saved);
    out.put("skipped", skipped);
    return out;
  }

  private ClassroomResponseEntity saveResponse(
      TenantScope scope, UUID itemId, Map<String, Object> body) {
    ClassroomResponseEntity entity = new ClassroomResponseEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setItemId(itemId);
    entity.setAdmissionNo(text(body.get("admissionNo"), null));
    entity.setStudentName(text(body.get("studentName"), null));
    entity.setScore(decimal(body.get("score")));
    entity.setStatus(text(body.get("status"), "SUBMITTED").toUpperCase(Locale.ROOT));
    entity.setPayload(payloadOf(body.get("payload")));
    entity.setCreatedAt(Instant.now());
    return responses.save(entity);
  }

  private ClassroomItemEntity requireItem(UUID id, String organizationId) {
    return items
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ExamException("NOT_FOUND", "Classroom item not found"));
  }

  private static String normalizeKind(String kind) {
    String normalized = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
    if (!KINDS.contains(normalized)) {
      throw new ExamException("VALIDATION", "Unsupported classroom kind");
    }
    return normalized;
  }

  private static BigDecimal decimal(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Map<String, Object> payloadOf(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      map.forEach((key, value) -> out.put(String.valueOf(key), value));
      return out;
    }
    return new LinkedHashMap<>();
  }

  private static String text(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? fallback : text;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private Map<String, Object> toDto(ClassroomItemEntity entity) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", entity.getId().toString());
    row.put("kind", entity.getKind());
    row.put("status", entity.getStatus());
    row.put("title", entity.getTitle());
    row.put("note", entity.getNote());
    row.put("payload", entity.getPayload());
    row.put("createdBy", entity.getCreatedBy());
    row.put("createdAt", entity.getCreatedAt().toString());
    return row;
  }

  private Map<String, Object> toResponse(ClassroomResponseEntity entity) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", entity.getId().toString());
    row.put("itemId", entity.getItemId().toString());
    row.put("admissionNo", entity.getAdmissionNo());
    row.put("studentName", entity.getStudentName());
    row.put("score", entity.getScore());
    row.put("status", entity.getStatus());
    row.put("payload", entity.getPayload());
    row.put("createdAt", entity.getCreatedAt().toString());
    return row;
  }
}
