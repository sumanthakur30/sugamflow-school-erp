package com.sugamflow.school.student.service;

import com.sugamflow.school.common.security.BranchAccess;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.config.StudentProperties;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentAttachmentEntity;
import com.sugamflow.school.student.persistence.entity.StudentDocumentEntity;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentAttachmentRepository;
import com.sugamflow.school.student.persistence.repo.StudentDocumentRepository;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentDocumentService {

  private static final DateTimeFormatter CARD_DATE =
      DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

  public static final String TYPE_ID_CARD = "ID_CARD";
  public static final String TYPE_BONAFIDE = "BONAFIDE";
  public static final String TYPE_CHARACTER = "CHARACTER_CERTIFICATE";

  private final StudentDocumentRepository documents;
  private final StudentRecordRepository students;
  private final StudentAttachmentRepository attachments;
  private final ConfigEngineClient engines;
  private final StudentProperties properties;

  public StudentDocumentService(
      StudentDocumentRepository documents,
      StudentRecordRepository students,
      StudentAttachmentRepository attachments,
      ConfigEngineClient engines,
      StudentProperties properties) {
    this.documents = documents;
    this.students = students;
    this.attachments = attachments;
    this.engines = engines;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listForStudent(UUID studentId) {
    TenantScope scope = TenantContext.require();
    StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
    try {
      BranchAccess.requireEntityBranch(scope, student.getBranchId());
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
    return documents
        .findByOrganizationIdAndStudentIdOrderByIssuedAtDesc(scope.organizationId(), studentId)
        .stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> issue(UUID studentId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
    StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
    try {
      BranchAccess.requireEntityBranch(scope, student.getBranchId());
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
    String type = normalizeType(String.valueOf(body.getOrDefault("type", "")));
    String templateKey = templateFor(type, body.get("templateKey"));
    String token = UUID.randomUUID().toString().replace("-", "");
    String verifyUrl = publicVerifyUrl(token);
    Instant now = Instant.now();
    String referenceNo =
        type.replace('_', '-')
            + "-"
            + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneOffset.UTC)
                .format(now)
            + "-"
            + token.substring(0, 6).toUpperCase(Locale.ROOT);

    Map<String, Object> studentDto = StudentRecordService.toIdentitySummary(studentAnswersDto(student));
    StudentPhoto photo = resolveStudentPhoto(scope.organizationId(), studentId, student);
    Map<String, Object> studentPayload =
        buildIdCardStudentPayload(scope, student, studentDto, photo, type);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("student", studentPayload);
    data.put(
        "document",
        Map.of(
            "type", type,
            "referenceNo", referenceNo,
            "templateKey", templateKey));
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", scope.organizationId());
    context.put("organizationName", humanizeId(scope.organizationId()));
    context.put("branchId", scope.branchId());
    context.put("branchName", humanizeId(scope.branchId()));
    context.put("academicSessionId", scope.academicSessionId());
    context.put("sessionLabel", sessionLabel(scope.academicSessionId()));
    context.put("issuedAt", CARD_DATE.format(now));
    String expiresAt = stringOr(body.get("expiresAt"), "");
    context.put("expiresAt", expiresAt.isBlank() ? "—" : shortDate(expiresAt));
    context.put("verifyUrl", verifyUrl);
    context.put("verificationToken", token);
    data.put("context", context);

    Map<String, Object> rendered = engines.renderReport(scope, templateKey, data);
    if (rendered == null || rendered.get("contentBase64") == null) {
      throw new StudentException("RENDER_FAILED", "Report render returned no PDF for " + templateKey);
    }

    StudentDocumentEntity entity = new StudentDocumentEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setStudentId(studentId);
    entity.setAdmissionNo(student.getAdmissionNo());
    entity.setDocumentType(type);
    entity.setTemplateKey(templateKey);
    entity.setReferenceNo(referenceNo);
    entity.setVerificationToken(token);
    entity.setStatus("ISSUED");
    entity.setFileName(String.valueOf(rendered.getOrDefault("fileName", templateKey + ".pdf")));
    entity.setContentType(String.valueOf(rendered.getOrDefault("contentType", "application/pdf")));
    entity.setContentBase64(String.valueOf(rendered.get("contentBase64")));
    Object bytes = rendered.get("byteLength");
    entity.setByteLength(bytes instanceof Number n ? n.intValue() : null);
    entity.setIssuedBy(scope.userId());
    entity.setIssuedAt(now);
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("verifyUrl", verifyUrl);
    meta.put("studentName", stringOr(studentDto.get("fullName"), ""));
    meta.put("classSection", stringOr(studentDto.get("classSection"), ""));
    if (!expiresAt.isBlank()) {
      meta.put("expiresAt", expiresAt);
    }
    entity.setMeta(meta);
    return toDto(documents.save(entity));
  }

  /** Issued documents whose expiry still matches the document rule. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> expiring() {
    TenantScope scope = TenantContext.require();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (StudentDocumentEntity entity :
        documents.findTop200ByOrganizationIdAndStatusOrderByIssuedAtDesc(
            scope.organizationId(), "ISSUED")) {
      if (rows.size() >= 25) {
        break;
      }
      Object raw = entity.getMeta() == null ? null : entity.getMeta().get("expiresAt");
      if (raw == null || String.valueOf(raw).isBlank()) {
        continue;
      }
      LocalDate expires;
      try {
        expires = LocalDate.parse(String.valueOf(raw).substring(0, 10));
      } catch (RuntimeException ex) {
        continue;
      }
      long days = ChronoUnit.DAYS.between(today, expires);
      boolean matched;
      try {
        matched =
            engines
                .evaluateRules(scope, Map.of("document", Map.of("daysToExpiry", days)))
                .contains("NOTIFY_DOCUMENT");
      } catch (RuntimeException ex) {
        matched = days <= 30;
      }
      if (!matched) {
        continue;
      }
      Map<String, Object> row = toDto(entity);
      row.put("daysToExpiry", days);
      row.put("expiresAt", expires.toString());
      rows.add(row);
    }
    return rows;
  }

  @Transactional(readOnly = true)
  public byte[] pdfBytes(UUID documentId) {
    TenantScope scope = TenantContext.require();
    StudentDocumentEntity entity =
        documents
            .findByIdAndOrganizationId(documentId, scope.organizationId())
            .orElseThrow(() -> new StudentException("NOT_FOUND", "Document not found"));
    return decodePdf(entity);
  }

  @Transactional
  public Map<String, Object> revoke(UUID documentId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
    StudentDocumentEntity entity =
        documents
            .findByIdAndOrganizationId(documentId, scope.organizationId())
            .orElseThrow(() -> new StudentException("NOT_FOUND", "Document not found"));
    if ("REVOKED".equalsIgnoreCase(entity.getStatus())) {
      return toDto(entity);
    }
    entity.setStatus("REVOKED");
    entity.setRevokedAt(Instant.now());
    entity.setRevokeReason(
        body != null && body.get("reason") != null
            ? String.valueOf(body.get("reason"))
            : "Revoked by staff");
    return toDto(documents.save(entity));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> verifyPublic(String token) {
    if (token == null || token.isBlank()) {
      throw new StudentException("NOT_FOUND", "Verification token is required");
    }
    StudentDocumentEntity entity =
        documents
            .findByVerificationToken(token.trim())
            .orElseThrow(() -> new StudentException("NOT_FOUND", "Document not found"));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("valid", "ISSUED".equalsIgnoreCase(entity.getStatus()));
    out.put("status", entity.getStatus());
    out.put("documentType", entity.getDocumentType());
    out.put("referenceNo", entity.getReferenceNo());
    out.put("admissionNo", entity.getAdmissionNo());
    out.put("organizationId", entity.getOrganizationId());
    out.put("branchId", entity.getBranchId());
    out.put("academicSessionId", entity.getAcademicSessionId());
    out.put("issuedAt", entity.getIssuedAt().toString());
    out.put("revokedAt", entity.getRevokedAt() != null ? entity.getRevokedAt().toString() : null);
    out.put("studentName", entity.getMeta().get("studentName"));
    out.put("classSection", entity.getMeta().get("classSection"));
    out.put(
        "message",
        "ISSUED".equalsIgnoreCase(entity.getStatus())
            ? "Document is authentic and currently valid."
            : "Document was revoked and is no longer valid.");
    return out;
  }

  private StudentRecordEntity requireStudent(UUID studentId, String org) {
    return students
        .findByIdAndOrganizationId(studentId, org)
        .orElseThrow(() -> new StudentException("NOT_FOUND", "Student not found"));
  }

  private Map<String, Object> studentAnswersDto(StudentRecordEntity student) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", student.getId().toString());
    dto.put("admissionNo", student.getAdmissionNo());
    dto.put("status", student.getStatus());
    dto.put("branchId", student.getBranchId());
    dto.put("academicSessionId", student.getAcademicSessionId());
    dto.put("answers", student.getAnswers());
    return dto;
  }

  private static String normalizeType(String raw) {
    String type = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    return switch (type) {
      case TYPE_ID_CARD, "ID", "STUDENT_ID" -> TYPE_ID_CARD;
      case TYPE_BONAFIDE, "BONAFIDE_CERTIFICATE" -> TYPE_BONAFIDE;
      case TYPE_CHARACTER, "CHARACTER" -> TYPE_CHARACTER;
      default -> throw new StudentException(
          "VALIDATION",
          "Supported document types: ID_CARD, BONAFIDE, CHARACTER_CERTIFICATE");
    };
  }

  private static String templateFor(String type, Object override) {
    if (override != null && !String.valueOf(override).isBlank()) {
      return String.valueOf(override).trim();
    }
    return switch (type) {
      case TYPE_ID_CARD -> "id_card";
      case TYPE_BONAFIDE -> "bonafide";
      case TYPE_CHARACTER -> "character_certificate";
      default -> throw new StudentException("VALIDATION", "Unknown document type: " + type);
    };
  }

  private String publicVerifyUrl(String token) {
    String base = properties.getIntegrations().getPublicUiBaseUrl();
    if (base == null || base.isBlank()) {
      base = properties.getIntegrations().getPublicApiBaseUrl();
    }
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (base.contains("9090")) {
      return base + "/api/student/public/documents/verify/" + token;
    }
    return base + "/verify/document/" + token;
  }

  private static byte[] decodePdf(StudentDocumentEntity entity) {
    if (entity.getContentBase64() == null || entity.getContentBase64().isBlank()) {
      throw new StudentException("PDF_MISSING", "PDF content is missing for this document");
    }
    try {
      return Base64.getDecoder().decode(entity.getContentBase64());
    } catch (IllegalArgumentException ex) {
      throw new StudentException("PDF_CORRUPT", "Stored PDF is not valid base64");
    }
  }

  private Map<String, Object> toDto(StudentDocumentEntity e) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", e.getId().toString());
    dto.put("studentId", e.getStudentId().toString());
    dto.put("admissionNo", e.getAdmissionNo());
    dto.put("documentType", e.getDocumentType());
    dto.put("templateKey", e.getTemplateKey());
    dto.put("referenceNo", e.getReferenceNo());
    dto.put("status", e.getStatus());
    dto.put("fileName", e.getFileName());
    dto.put("contentType", e.getContentType());
    dto.put("byteLength", e.getByteLength());
    dto.put("issuedBy", e.getIssuedBy());
    dto.put("issuedAt", e.getIssuedAt().toString());
    dto.put("revokedAt", e.getRevokedAt() != null ? e.getRevokedAt().toString() : null);
    dto.put("revokeReason", e.getRevokeReason());
    dto.put("verifyUrl", e.getMeta().get("verifyUrl"));
    dto.put("verificationToken", e.getVerificationToken());
    dto.put("studentName", e.getMeta().get("studentName"));
    dto.put("classSection", e.getMeta().get("classSection"));
    dto.put("downloadPath", "/api/student/documents/" + e.getId() + "/pdf");
    return dto;
  }

  /**
   * Build ID-card student payload. For ID_CARD, only include values whose form fields have
   * {@code showOnIdCard=true} (photo always kept for layout). Other document types keep full payload.
   */
  private Map<String, Object> buildIdCardStudentPayload(
      TenantScope scope,
      StudentRecordEntity student,
      Map<String, Object> studentDto,
      StudentPhoto photo,
      String documentType) {
    String contentUrl =
        photo.contentUrl().isBlank()
            ? stringOr(studentDto.get("photoUrl"), "")
            : photo.contentUrl();
    Map<String, Object> full = new LinkedHashMap<>();
    full.put("name", stringOr(studentDto.get("fullName"), "Student"));
    full.put("fullName", stringOr(studentDto.get("fullName"), "Student"));
    full.put("admissionNo", stringOr(student.getAdmissionNo(), ""));
    full.put("classSection", dashIfBlank(stringOr(studentDto.get("classSection"), "")));
    full.put("classApplied", stringOr(studentDto.get("classSection"), ""));
    full.put("rollNo", stringOr(studentDto.get("rollNo"), ""));
    full.put("house", stringOr(studentDto.get("house"), ""));
    full.put("gender", stringOr(studentDto.get("gender"), ""));
    full.put("photoUrl", contentUrl);
    full.put("photoContentUrl", contentUrl);
    full.put("photoBase64", photo.base64());
    full.put("photoDirectUrl", photo.dataUrl());
    Map<String, Object> answers = student.getAnswers() != null ? student.getAnswers() : Map.of();
    String rollNo = stringOr(studentDto.get("rollNo"), "");
    String admissionNo = stringOr(student.getAdmissionNo(), "");
    full.put(
        "rollLine",
        rollNo.isBlank() ? "Adm  " + admissionNo : "Roll  " + rollNo + "  ·  " + admissionNo);
    full.put("dateOfBirth", dobLabel(firstAnswer(answers, "dateOfBirth", "dob")));
    full.put("bloodGroup", dashIfBlank(firstAnswer(answers, "bloodGroup", "blood_group")));
    full.put("emergencyContact", dashIfBlank(emergencyPhone(answers)));
    full.put("transportMode", transportLabel(answers));
    full.put("penNumber", dashIfBlank(stringOr(studentDto.get("penNumber"), "")));
    full.put("apaarId", dashIfBlank(stringOr(studentDto.get("apaarId"), "")));
    full.put("samagraId", stringOr(studentDto.get("samagraId"), ""));
    full.put("schoolStudentId", stringOr(studentDto.get("schoolStudentId"), ""));
    if (!TYPE_ID_CARD.equals(documentType)) {
      return full;
    }
    java.util.Set<String> allowed = idCardAllowedKeys(scope);
    if (allowed.isEmpty()) {
      return full;
    }
    // Always keep visual identity anchors used by the template shell.
    allowed.add("name");
    allowed.add("fullName");
    allowed.add("admissionNo");
    allowed.add("photoBase64");
    allowed.add("photoUrl");
    allowed.add("photoDirectUrl");
    allowed.add("photoContentUrl");
    allowed.add("classSection");
    allowed.add("rollLine");
    allowed.add("dateOfBirth");
    allowed.add("bloodGroup");
    allowed.add("emergencyContact");
    allowed.add("transportMode");
    allowed.add("penNumber");
    allowed.add("apaarId");
    Map<String, Object> filtered = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : full.entrySet()) {
      filtered.put(e.getKey(), allowed.contains(e.getKey()) ? e.getValue() : "");
    }
    return filtered;
  }

  @SuppressWarnings("unchecked")
  private java.util.Set<String> idCardAllowedKeys(TenantScope scope) {
    java.util.Set<String> keys = new java.util.LinkedHashSet<>();
    try {
      Map<String, Object> module = engines.getModuleSettings(scope, "student");
      String formKey = "student_master";
      if (module != null) {
        Object settings = module.get("settings");
        if (settings instanceof Map<?, ?> sm && sm.get("formKey") != null) {
          String fk = String.valueOf(sm.get("formKey")).trim();
          if (!fk.isEmpty()) {
            formKey = fk;
          }
        } else if (module.get("formKey") != null) {
          String fk = String.valueOf(module.get("formKey")).trim();
          if (!fk.isEmpty()) {
            formKey = fk;
          }
        }
      }
      Map<String, Object> form = engines.getForm(scope, formKey);
      if (form == null) {
        return keys;
      }
      Object sectionsObj = form.get("sections");
      if (!(sectionsObj instanceof List<?> sections)) {
        return keys;
      }
      boolean sawFlag = false;
      for (Object sectionObj : sections) {
        if (!(sectionObj instanceof Map<?, ?>)) {
          continue;
        }
        Map<String, Object> section = (Map<String, Object>) sectionObj;
        Object fieldsObj = section.get("fields");
        if (!(fieldsObj instanceof List<?> fields)) {
          continue;
        }
        for (Object fieldObj : fields) {
          if (!(fieldObj instanceof Map<?, ?>)) {
            continue;
          }
          Map<String, Object> field = (Map<String, Object>) fieldObj;
          if (!field.containsKey("showOnIdCard")) {
            continue;
          }
          sawFlag = true;
          if (!truthy(field.get("showOnIdCard"))) {
            continue;
          }
          String key = String.valueOf(field.get("key")).trim();
          if (key.isEmpty()) {
            continue;
          }
          keys.add(key);
          // Template bind aliases
          if ("fullName".equals(key)) {
            keys.add("name");
          }
          if ("classApplied".equals(key) || "classSection".equals(key)) {
            keys.add("classSection");
            keys.add("classApplied");
          }
        }
      }
      if (!sawFlag) {
        keys.clear();
      }
    } catch (Exception ignored) {
      keys.clear();
    }
    return keys;
  }

  private static boolean truthy(Object v) {
    if (v instanceof Boolean b) {
      return b;
    }
    if (v == null) {
      return false;
    }
    String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
    return "true".equals(s) || "1".equals(s) || "yes".equals(s);
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? fallback : s;
  }

  private static String firstAnswer(Map<String, Object> answers, String... keys) {
    for (String key : keys) {
      String value = stringOr(answers.get(key), "");
      if (!value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private static String dashIfBlank(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  /** "demo-school" → "Demo School". */
  private static String humanizeId(String raw) {
    if (raw == null || raw.isBlank()) {
      return "School";
    }
    String[] parts = raw.trim().replace('_', ' ').replace('-', ' ').split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        sb.append(part.substring(1).toLowerCase(Locale.ROOT));
      }
    }
    return sb.isEmpty() ? "School" : sb.toString();
  }

  /** "2025-26" → "2025-2026". Other session ids stay as stored. */
  private static String sessionLabel(String raw) {
    if (raw == null || raw.isBlank()) {
      return "—";
    }
    String value = raw.trim();
    if (value.matches("\\d{4}-\\d{2}")) {
      int start = Integer.parseInt(value.substring(0, 4));
      return start + "-" + (start + 1);
    }
    return value;
  }

  private static String dobLabel(String raw) {
    if (raw == null || raw.isBlank()) {
      return "—";
    }
    String value = raw.trim();
    try {
      if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
        return LocalDate.parse(value.substring(0, 10))
            .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
      }
    } catch (RuntimeException ignored) {
      return value;
    }
    return value;
  }

  private static String emergencyPhone(Map<String, Object> answers) {
    String direct =
        firstAnswer(
            answers,
            "emergencyContact",
            "emergencyMobile",
            "guardianMobile",
            "fatherMobile",
            "parentMobile",
            "mobile");
    if (!direct.isBlank()) {
      return direct;
    }
    Object guardians = answers.get("guardians");
    if (!(guardians instanceof List<?> list)) {
      return "";
    }
    String fallback = "";
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> row)) {
        continue;
      }
      String mobile = stringOr(row.get("mobile"), stringOr(row.get("phone"), ""));
      if (mobile.isBlank()) {
        continue;
      }
      Object primary = row.get("isPrimary");
      if (Boolean.TRUE.equals(primary) || "true".equalsIgnoreCase(String.valueOf(primary))) {
        return mobile;
      }
      if (fallback.isBlank()) {
        fallback = mobile;
      }
    }
    return fallback;
  }

  private static String transportLabel(Map<String, Object> answers) {
    String mode = firstAnswer(answers, "transportMode", "modeOfTransport", "conveyance");
    if (!mode.isBlank()) {
      return humanizeId(mode);
    }
    if (!answers.containsKey("transport")) {
      return "—";
    }
    Object flag = answers.get("transport");
    boolean on =
        Boolean.TRUE.equals(flag)
            || "true".equalsIgnoreCase(String.valueOf(flag))
            || "yes".equalsIgnoreCase(String.valueOf(flag))
            || "1".equals(String.valueOf(flag).trim());
    return on ? "Bus" : "Walker";
  }

  private static String shortDate(String raw) {
    String value = raw.trim();
    try {
      if (value.length() >= 20 && value.contains("T")) {
        return CARD_DATE.format(Instant.parse(value));
      }
      if (value.length() >= 10) {
        return LocalDate.parse(value.substring(0, 10))
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
      }
    } catch (RuntimeException ignored) {
      return value;
    }
    return value;
  }

  /**
   * Prefer vault STUDENT_PHOTO bytes. {@code photoDirectUrl} is a data URL so the ID card canvas
   * and PDF can draw it without a second authenticated fetch.
   */
  private StudentPhoto resolveStudentPhoto(
      String organizationId, UUID studentId, StudentRecordEntity student) {
    List<StudentAttachmentEntity> photos =
        attachments.findByOrganizationIdAndStudentIdAndAttachmentTypeOrderByCreatedAtDesc(
            organizationId, studentId, StudentAttachmentService.TYPE_STUDENT_PHOTO);
    if (!photos.isEmpty()) {
      StudentAttachmentEntity photo = photos.get(0);
      String b64 = stripBase64(photo.getContentBase64());
      String mime = photo.getContentType();
      if (mime == null || !mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
        mime = "image/jpeg";
      }
      String dataUrl = b64.isBlank() ? "" : "data:" + mime + ";base64," + b64;
      String contentUrl = "/api/student/attachments/" + photo.getId() + "/content";
      return new StudentPhoto(b64, dataUrl, contentUrl);
    }
    Map<String, Object> answers = student.getAnswers() != null ? student.getAnswers() : Map.of();
    String raw = stringOr(answers.get("photoBase64"), "");
    String b64 = stripBase64(raw);
    String dataUrl = "";
    if (!b64.isBlank()) {
      dataUrl = raw.startsWith("data:") ? raw : "data:image/jpeg;base64," + b64;
    }
    return new StudentPhoto(b64, dataUrl, stringOr(answers.get("photoUrl"), ""));
  }

  private static String stripBase64(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String trimmed = value.trim();
    int comma = trimmed.indexOf(',');
    if (trimmed.regionMatches(true, 0, "data:", 0, 5) && comma > 0) {
      return trimmed.substring(comma + 1);
    }
    return trimmed;
  }

  private record StudentPhoto(String base64, String dataUrl, String contentUrl) {}
}
