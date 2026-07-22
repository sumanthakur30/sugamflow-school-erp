package com.sugamflow.school.student.service;

import com.sugamflow.school.common.security.BranchAccess;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.config.StudentProperties;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentDocumentEntity;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentDocumentRepository;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

  public static final String TYPE_ID_CARD = "ID_CARD";
  public static final String TYPE_BONAFIDE = "BONAFIDE";
  public static final String TYPE_CHARACTER = "CHARACTER_CERTIFICATE";

  private final StudentDocumentRepository documents;
  private final StudentRecordRepository students;
  private final ConfigEngineClient engines;
  private final StudentProperties properties;

  public StudentDocumentService(
      StudentDocumentRepository documents,
      StudentRecordRepository students,
      ConfigEngineClient engines,
      StudentProperties properties) {
    this.documents = documents;
    this.students = students;
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
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(
        "student",
        Map.of(
            "name",
            stringOr(studentDto.get("fullName"), "Student"),
            "admissionNo",
            stringOr(student.getAdmissionNo(), ""),
            "classSection",
            stringOr(studentDto.get("classSection"), ""),
            "house",
            stringOr(studentDto.get("house"), ""),
            "gender",
            stringOr(studentDto.get("gender"), "")));
    data.put(
        "document",
        Map.of(
            "type", type,
            "referenceNo", referenceNo,
            "templateKey", templateKey));
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", scope.organizationId());
    context.put("branchId", scope.branchId());
    context.put("academicSessionId", scope.academicSessionId());
    context.put("issuedAt", now.toString());
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
    entity.setMeta(
        Map.of(
            "verifyUrl",
            verifyUrl,
            "studentName",
            stringOr(studentDto.get("fullName"), ""),
            "classSection",
            stringOr(studentDto.get("classSection"), "")));
    return toDto(documents.save(entity));
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

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? fallback : s;
  }
}
