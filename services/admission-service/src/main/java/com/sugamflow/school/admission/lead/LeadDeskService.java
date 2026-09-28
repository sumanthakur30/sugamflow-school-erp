package com.sugamflow.school.admission.lead;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sugamflow.school.admission.integration.ConfigEngineClient;
import com.sugamflow.school.admission.persistence.entity.AdmissionLeadEntity;
import com.sugamflow.school.admission.persistence.repo.AdmissionLeadRepository;
import com.sugamflow.school.admission.service.AdmissionApplicationService;
import com.sugamflow.school.admission.web.AdmissionException;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LeadDeskService {

  static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter DISPLAY =
      DateTimeFormatter.ofPattern("dd MMM, yyyy hh:mm:ssa", Locale.ENGLISH).withZone(SCHOOL_ZONE);
  private static final int EXPORT_LIMIT = 2000;

  private final AdmissionLeadRepository repository;
  private final ConfigEngineClient engines;

  public LeadDeskService(AdmissionLeadRepository repository, ConfigEngineClient engines) {
    this.repository = repository;
    this.engines = engines;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(
      Integer page,
      Integer size,
      String q,
      String status,
      String classAppliedFor,
      String assignedTo,
      String scheduledFrom,
      String scheduledTo) {
    TenantScope scope = requireScope();
    int safePage = page == null || page < 0 ? 0 : page;
    int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
    Page<AdmissionLeadEntity> result =
        repository.findAll(
            spec(scope.organizationId(), q, status, classAppliedFor, assignedTo, scheduledFrom, scheduledTo),
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    List<Map<String, Object>> items = result.getContent().stream().map(this::toDto).toList();
    return PageResult.of(items, safePage, safeSize, result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary() {
    TenantScope scope = requireScope();
    Map<String, Long> counts = new LinkedHashMap<>();
    for (LeadStatus status : LeadStatus.values()) {
      counts.put(status.name(), 0L);
    }
    long total = 0;
    for (Object[] row : repository.countByStatus(scope.organizationId())) {
      String key = String.valueOf(row[0]);
      long count = ((Number) row[1]).longValue();
      counts.put(key, count);
      total += count;
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("total", total);
    body.put("counts", counts);
    return body;
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    TenantScope scope = requireScope();
    AdmissionLeadEntity entity = new AdmissionLeadEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setCreatedAt(Instant.now());
    entity.setCreatedBy(blankTo(string(body.get("createdBy")), actor(scope)));
    apply(entity, body, true);
    entity.setUpdatedAt(Instant.now());
    return toDto(repository.save(entity));
  }

  @Transactional
  public Map<String, Object> update(UUID id, Map<String, Object> body) {
    TenantScope scope = requireScope();
    AdmissionLeadEntity entity = requireLead(id, scope.organizationId());
    apply(entity, body, false);
    entity.setUpdatedAt(Instant.now());
    return toDto(repository.save(entity));
  }

  @Transactional
  public Map<String, Object> patchStatus(UUID id, Map<String, Object> body) {
    TenantScope scope = requireScope();
    AdmissionLeadEntity entity = requireLead(id, scope.organizationId());
    entity.setStatus(LeadStatus.parse(string(body.get("status"))).name());
    entity.setUpdatedAt(Instant.now());
    return toDto(repository.save(entity));
  }

  @Transactional
  public Map<String, Object> importFile(MultipartFile file) {
    TenantScope scope = requireScope();
    if (file == null || file.isEmpty()) {
      throw new AdmissionException("VALIDATION", "Choose a CSV or Excel file.");
    }
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    List<LeadImportParser.ImportedLead> rows;
    try {
      if (name.endsWith(".xlsx")) {
        rows = LeadImportParser.parseXlsx(file.getInputStream());
      } else if (name.endsWith(".csv") || name.endsWith(".txt") || name.isBlank()) {
        rows = LeadImportParser.parseCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
      } else {
        throw new AdmissionException("VALIDATION", "Upload a .csv or .xlsx file.");
      }
    } catch (AdmissionException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AdmissionException("VALIDATION", "Could not read the import file.");
    }
    int created = 0;
    List<String> errors = new ArrayList<>();
    for (LeadImportParser.ImportedLead row : rows) {
      try {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentName", row.studentName());
        body.put("fatherName", row.fatherName());
        body.put("motherName", row.motherName());
        body.put("phone", row.phone());
        body.put("fatherPhone", row.fatherPhone());
        body.put("motherPhone", row.motherPhone());
        body.put("address", row.address());
        body.put("classAppliedFor", row.classAppliedFor());
        body.put("scheduledAt", row.scheduledAt());
        body.put("status", row.status());
        body.put("remark", row.remark());
        body.put("assignedTo", row.assignedTo());
        body.put("admissionNo", row.admissionNo());
        AdmissionLeadEntity entity = new AdmissionLeadEntity();
        entity.setId(UUID.randomUUID());
        entity.setOrganizationId(scope.organizationId());
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(actor(scope));
        apply(entity, body, true);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        created++;
      } catch (AdmissionException ex) {
        errors.add("Row " + row.line() + ": " + ex.getMessage());
      }
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("created", created);
    result.put("skipped", errors.size());
    result.put("errors", errors);
    return result;
  }

  @Transactional
  public Map<String, Object> loadExample() {
    TenantScope scope = requireScope();
    if (repository.existsByOrganizationIdAndExampleSeedTrue(scope.organizationId())) {
      Map<String, Object> already = new LinkedHashMap<>();
      already.put("created", 0);
      already.put("message", "Sample leads are already on this school.");
      return already;
    }
    String campus = "Sample school";
    createExample(scope, "Aman Jain", "Kishan Saroj", "", "9875486857", "", "", "", "", LeadStatus.INTERESTED, "ssfsf", "", at(2026, 8, 2, 16, 41), at(2026, 9, 9, 8, 12), campus);
    createExample(scope, "Kk Pp", "", "", "0000000000", "", "", "", "Nursery", LeadStatus.INTERESTED, "", "", at(2026, 7, 30, 4, 41), null, campus);
    createExample(scope, "Jsjsjs Djdjnd", "", "Dbjdnd", "9464916691", "", "", "Dndjd", "Nursery", LeadStatus.INTERESTED, "", "Raghvendra Yadav", at(2026, 6, 19, 9, 54), at(2026, 7, 3, 8, 0), campus);
    Map<String, Object> created = new LinkedHashMap<>();
    created.put("created", 3);
    created.put("message", "Loaded 3 sample leads for this school.");
    return created;
  }

  @Transactional(readOnly = true)
  public byte[] exportExcel(
      String q, String status, String classAppliedFor, String assignedTo, String scheduledFrom, String scheduledTo) {
    List<AdmissionLeadEntity> rows =
        matching(q, status, classAppliedFor, assignedTo, scheduledFrom, scheduledTo);
    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Leads");
      Row header = sheet.createRow(0);
      String[] titles = {
        "Student", "Father", "Mother", "Mobile", "Father mobile", "Mother mobile", "Address",
        "Applied for", "Created by", "Created at", "Scheduled at", "Status", "Remark", "Assigned to"
      };
      for (int i = 0; i < titles.length; i++) {
        header.createCell(i).setCellValue(titles[i]);
      }
      int r = 1;
      for (AdmissionLeadEntity lead : rows) {
        Row row = sheet.createRow(r++);
        row.createCell(0).setCellValue(text(lead.getStudentName()));
        row.createCell(1).setCellValue(text(lead.getFatherName()));
        row.createCell(2).setCellValue(text(lead.getMotherName()));
        row.createCell(3).setCellValue(text(lead.getPhone()));
        row.createCell(4).setCellValue(text(lead.getFatherPhone()));
        row.createCell(5).setCellValue(text(lead.getMotherPhone()));
        row.createCell(6).setCellValue(text(lead.getAddress()));
        row.createCell(7).setCellValue(text(lead.getClassAppliedFor()));
        row.createCell(8).setCellValue(text(lead.getCreatedBy()));
        row.createCell(9).setCellValue(format(lead.getCreatedAt()));
        row.createCell(10).setCellValue(format(lead.getScheduledAt()));
        row.createCell(11).setCellValue(LeadStatus.parse(lead.getStatus()).label());
        row.createCell(12).setCellValue(text(lead.getRemark()));
        row.createCell(13).setCellValue(text(lead.getAssignedTo()));
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (Exception ex) {
      throw new AdmissionException("EXPORT", "Could not build the Excel file.");
    }
  }

  @Transactional(readOnly = true)
  public byte[] exportPdf(
      String q, String status, String classAppliedFor, String assignedTo, String scheduledFrom, String scheduledTo) {
    List<AdmissionLeadEntity> rows =
        matching(q, status, classAppliedFor, assignedTo, scheduledFrom, scheduledTo);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document document = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
      PdfWriter.getInstance(document, out);
      document.open();
      Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
      Font small = FontFactory.getFont(FontFactory.HELVETICA, 8);
      document.add(new Phrase("Leads / Inquiry", title));
      document.add(new Phrase("\n"));
      PdfPTable table = new PdfPTable(new float[] {2.2f, 2f, 1.6f, 1.2f, 1.8f, 1.6f, 1.4f, 2.2f});
      table.setWidthPercentage(100);
      for (String head : List.of("Student", "Contact", "Address", "Applied for", "Created", "Scheduled", "Status", "Remark")) {
        PdfPCell cell = new PdfPCell(new Phrase(head, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8)));
        table.addCell(cell);
      }
      for (AdmissionLeadEntity lead : rows) {
        table.addCell(new Phrase(join(lead.getStudentName(), lead.getFatherName(), lead.getMotherName()), small));
        table.addCell(new Phrase(join(lead.getPhone(), lead.getFatherPhone(), lead.getMotherPhone()), small));
        table.addCell(new Phrase(text(lead.getAddress()), small));
        table.addCell(new Phrase(text(lead.getClassAppliedFor()), small));
        table.addCell(new Phrase(text(lead.getCreatedBy()) + "\n" + format(lead.getCreatedAt()), small));
        table.addCell(new Phrase(format(lead.getScheduledAt()), small));
        table.addCell(new Phrase(LeadStatus.parse(lead.getStatus()).label(), small));
        String remark = text(lead.getRemark());
        if (lead.getAssignedTo() != null && !lead.getAssignedTo().isBlank()) {
          remark = (remark + " Assigned to " + lead.getAssignedTo()).trim();
        }
        table.addCell(new Phrase(remark, small));
      }
      document.add(table);
      document.close();
      return out.toByteArray();
    } catch (Exception ex) {
      throw new AdmissionException("EXPORT", "Could not build the PDF file.");
    }
  }

  private List<AdmissionLeadEntity> matching(
      String q, String status, String classAppliedFor, String assignedTo, String scheduledFrom, String scheduledTo) {
    TenantScope scope = requireScope();
    return repository
        .findAll(
            spec(scope.organizationId(), q, status, classAppliedFor, assignedTo, scheduledFrom, scheduledTo),
            PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
        .getContent();
  }

  private void createExample(
      TenantScope scope,
      String student,
      String father,
      String mother,
      String phone,
      String fatherPhone,
      String motherPhone,
      String address,
      String grade,
      LeadStatus status,
      String remark,
      String assignedTo,
      Instant createdAt,
      Instant scheduledAt,
      String campus) {
    AdmissionLeadEntity entity = new AdmissionLeadEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setStudentName(student);
    entity.setFatherName(blankToNull(father));
    entity.setMotherName(blankToNull(mother));
    entity.setPhone(phone);
    entity.setFatherPhone(blankToNull(fatherPhone));
    entity.setMotherPhone(blankToNull(motherPhone));
    entity.setAddress(blankToNull(address));
    entity.setClassAppliedFor(blankToNull(grade));
    entity.setStatus(status.name());
    entity.setRemark(blankToNull(remark));
    entity.setAssignedTo(blankToNull(assignedTo));
    entity.setCreatedBy(campus);
    entity.setCreatedAt(createdAt);
    entity.setScheduledAt(scheduledAt);
    entity.setExampleSeed(true);
    entity.setUpdatedAt(Instant.now());
    repository.save(entity);
  }

  private void apply(AdmissionLeadEntity entity, Map<String, Object> body, boolean creating) {
    String student = string(body.get("studentName"));
    String phone = string(body.get("phone"));
    if (creating || body.containsKey("studentName")) {
      if (student.isBlank()) {
        throw new AdmissionException("VALIDATION", "Student name is required.");
      }
      entity.setStudentName(trim(student, 160));
    }
    if (creating || body.containsKey("phone")) {
      if (phone.isBlank()) {
        throw new AdmissionException("VALIDATION", "Mobile number is required.");
      }
      entity.setPhone(trim(phone, 32));
    }
    if (creating || body.containsKey("fatherName")) {
      entity.setFatherName(blankToNull(trim(string(body.get("fatherName")), 160)));
    }
    if (creating || body.containsKey("motherName")) {
      entity.setMotherName(blankToNull(trim(string(body.get("motherName")), 160)));
    }
    if (creating || body.containsKey("fatherPhone")) {
      entity.setFatherPhone(blankToNull(trim(string(body.get("fatherPhone")), 32)));
    }
    if (creating || body.containsKey("motherPhone")) {
      entity.setMotherPhone(blankToNull(trim(string(body.get("motherPhone")), 32)));
    }
    if (creating || body.containsKey("address")) {
      entity.setAddress(blankToNull(trim(string(body.get("address")), 400)));
    }
    if (creating || body.containsKey("classAppliedFor")) {
      entity.setClassAppliedFor(blankToNull(trim(string(body.get("classAppliedFor")), 80)));
    }
    if (creating || body.containsKey("admissionNo")) {
      entity.setAdmissionNo(blankToNull(trim(string(body.get("admissionNo")), 64)));
    }
    if (creating || body.containsKey("remark")) {
      entity.setRemark(blankToNull(trim(string(body.get("remark")), 1000)));
    }
    if (creating || body.containsKey("assignedTo")) {
      entity.setAssignedTo(blankToNull(trim(string(body.get("assignedTo")), 160)));
    }
    if (creating || body.containsKey("scheduledAt")) {
      entity.setScheduledAt(parseWhen(string(body.get("scheduledAt"))));
    }
    if (creating || body.containsKey("status")) {
      entity.setStatus(LeadStatus.parse(string(body.get("status"))).name());
    }
  }

  private Specification<AdmissionLeadEntity> spec(
      String org,
      String q,
      String status,
      String classAppliedFor,
      String assignedTo,
      String scheduledFrom,
      String scheduledTo) {
    String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    String grade = classAppliedFor == null ? "" : classAppliedFor.trim().toLowerCase(Locale.ROOT);
    String assignee = assignedTo == null ? "" : assignedTo.trim().toLowerCase(Locale.ROOT);
    String statusName = status == null || status.isBlank() ? "" : LeadStatus.parse(status).name();
    Instant from = parseWhen(scheduledFrom);
    Instant to = parseWhen(scheduledTo);
    return (root, cq, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), org));
      if (!statusName.isBlank()) {
        predicates.add(cb.equal(root.get("status"), statusName));
      }
      if (!query.isBlank()) {
        String like = "%" + query + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("studentName")), like),
                cb.like(cb.lower(cb.coalesce(root.get("fatherName"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("motherName"), "")), like),
                cb.like(cb.lower(root.get("phone")), like),
                cb.like(cb.lower(cb.coalesce(root.get("fatherPhone"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("motherPhone"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("admissionNo"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("address"), "")), like)));
      }
      if (!grade.isBlank()) {
        predicates.add(cb.like(cb.lower(cb.coalesce(root.get("classAppliedFor"), "")), "%" + grade + "%"));
      }
      if (!assignee.isBlank()) {
        predicates.add(cb.like(cb.lower(cb.coalesce(root.get("assignedTo"), "")), "%" + assignee + "%"));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), to));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private AdmissionLeadEntity requireLead(UUID id, String organizationId) {
    return repository
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new AdmissionException("NOT_FOUND", "Lead not found."));
  }

  private TenantScope requireScope() {
    TenantScope scope = TenantContext.require();
    if (!engines.isFeatureEnabled(scope, AdmissionApplicationService.FEATURE_ADMISSION)) {
      throw new AdmissionException(
          "FEATURE_DISABLED", "FEATURE_ADMISSION is off for this subscription plan.");
    }
    return scope;
  }

  private Map<String, Object> toDto(AdmissionLeadEntity entity) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", entity.getId().toString());
    dto.put("studentName", entity.getStudentName());
    dto.put("fatherName", entity.getFatherName());
    dto.put("motherName", entity.getMotherName());
    dto.put("phone", entity.getPhone());
    dto.put("fatherPhone", entity.getFatherPhone());
    dto.put("motherPhone", entity.getMotherPhone());
    dto.put("address", entity.getAddress());
    dto.put("classAppliedFor", entity.getClassAppliedFor());
    dto.put("admissionNo", entity.getAdmissionNo());
    dto.put("createdBy", entity.getCreatedBy());
    dto.put("createdAt", entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString());
    dto.put("scheduledAt", entity.getScheduledAt() == null ? null : entity.getScheduledAt().toString());
    dto.put("status", entity.getStatus());
    dto.put("statusLabel", LeadStatus.parse(entity.getStatus()).label());
    dto.put("remark", entity.getRemark());
    dto.put("assignedTo", entity.getAssignedTo());
    dto.put("exampleSeed", entity.isExampleSeed());
    return dto;
  }

  static Instant parseWhen(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String value = raw.trim();
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ignored) {
      // local form values
    }
    try {
      LocalDateTime local =
          LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]"));
      return local.atZone(SCHOOL_ZONE).toInstant();
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
          .atZone(SCHOOL_ZONE)
          .toInstant();
    } catch (DateTimeParseException ex) {
      throw new AdmissionException("VALIDATION", "Use a date and time such as 2026-09-09T08:12.");
    }
  }

  private static String format(Instant instant) {
    return instant == null ? "" : DISPLAY.format(instant);
  }

  private static String actor(TenantScope scope) {
    if (scope.userId() != null && !scope.userId().isBlank()) {
      return scope.userId();
    }
    return scope.roleCode() == null ? "staff" : scope.roleCode();
  }

  private static String string(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String blankTo(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String trim(String value, int max) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  private static String join(String... parts) {
    StringBuilder out = new StringBuilder();
    for (String part : parts) {
      if (part == null || part.isBlank()) {
        continue;
      }
      if (!out.isEmpty()) {
        out.append('\n');
      }
      out.append(part);
    }
    return out.toString();
  }

  private static Instant at(int year, int month, int day, int hour, int minute) {
    return LocalDateTime.of(year, month, day, hour, minute).atZone(SCHOOL_ZONE).toInstant();
  }
}
