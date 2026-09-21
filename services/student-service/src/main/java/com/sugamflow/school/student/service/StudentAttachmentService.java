package com.sugamflow.school.student.service;

import com.sugamflow.school.common.security.BranchAccess;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentAttachmentEntity;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentAttachmentRepository;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student photo + KYC document vault. Distinct from issued certificates ({@code student_document}).
 * Uploads are JSON base64 (same pattern as existing document storage — no MultipartFile stack).
 */
@Service
public class StudentAttachmentService {

  public static final String TYPE_STUDENT_PHOTO = "STUDENT_PHOTO";
  public static final String TYPE_FATHER_PHOTO = "FATHER_PHOTO";
  public static final String TYPE_MOTHER_PHOTO = "MOTHER_PHOTO";
  public static final String TYPE_GUARDIAN_PHOTO = "GUARDIAN_PHOTO";

  private static final Set<String> PHOTO_TYPES =
      Set.of(TYPE_STUDENT_PHOTO, TYPE_FATHER_PHOTO, TYPE_MOTHER_PHOTO, TYPE_GUARDIAN_PHOTO);

  private static final Set<String> DOCUMENT_TYPES =
      Set.of(
          "BIRTH_CERTIFICATE",
          "TRANSFER_CERTIFICATE",
          "PREVIOUS_MARKSHEET",
          "AADHAAR_COPY",
          "INCOME_CERTIFICATE",
          "CASTE_CERTIFICATE",
          "MEDICAL_CERTIFICATE",
          "PASSPORT_PHOTO",
          "OTHER");

  private static final Set<String> IMAGE_MIME =
      Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

  private final StudentAttachmentRepository attachments;
  private final StudentRecordRepository students;
  private final ConfigEngineClient engines;

  public StudentAttachmentService(
      StudentAttachmentRepository attachments,
      StudentRecordRepository students,
      ConfigEngineClient engines) {
    this.attachments = attachments;
    this.students = students;
    this.engines = engines;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listForStudent(UUID studentId) {
    TenantScope scope = TenantContext.require();
    StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
    requireBranch(scope, student);
    return attachments
        .findByOrganizationIdAndStudentIdOrderByCreatedAtDesc(scope.organizationId(), studentId)
        .stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> upload(UUID studentId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireStaffWrite(scope);
    StudentRecordEntity student = requireStudent(studentId, scope.organizationId());
    requireBranch(scope, student);
    if (student.isDeleted()) {
      throw new StudentException("VALIDATION", "Restore student before uploading attachments");
    }

    Map<String, Object> settings = moduleSettings(scope);
    String type = normalizeType(String.valueOf(body.getOrDefault("type", "")));
    boolean photo = PHOTO_TYPES.contains(type);
    if (photo && !bool(settings.get("enableStudentPhoto"), true) && TYPE_STUDENT_PHOTO.equals(type)) {
      throw new StudentException("FEATURE_OFF", "Student photo upload is disabled in module settings");
    }
    if (photo
        && !TYPE_STUDENT_PHOTO.equals(type)
        && !bool(settings.get("enableGuardianPhoto"), true)) {
      throw new StudentException("FEATURE_OFF", "Guardian photo upload is disabled in module settings");
    }
    if (!photo && !bool(settings.get("enableDocumentVault"), true)) {
      throw new StudentException("FEATURE_OFF", "Document vault is disabled in module settings");
    }

    String contentType = String.valueOf(body.getOrDefault("contentType", "application/octet-stream"))
        .trim()
        .toLowerCase(Locale.ROOT);
    String fileName = String.valueOf(body.getOrDefault("fileName", type.toLowerCase(Locale.ROOT)))
        .trim();
    String b64 = String.valueOf(body.getOrDefault("contentBase64", "")).trim();
    if (b64.contains(",")) {
      // data URL prefix
      b64 = b64.substring(b64.indexOf(',') + 1);
    }
    if (b64.isBlank()) {
      throw new StudentException("VALIDATION", "contentBase64 is required");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(b64);
    } catch (IllegalArgumentException ex) {
      throw new StudentException("VALIDATION", "Invalid base64 content");
    }
    int maxKb =
        photo
            ? intOr(settings.get("maxPhotoKb"), 512)
            : intOr(settings.get("maxDocumentKb"), 2048);
    if (bytes.length > maxKb * 1024L) {
      throw new StudentException(
          "VALIDATION", "File exceeds maximum size of " + maxKb + " KB");
    }
    if (photo) {
      if (!IMAGE_MIME.contains(contentType) && !looksLikeImageName(fileName)) {
        throw new StudentException(
            "VALIDATION", "Photo must be JPG, JPEG, PNG, or WEBP");
      }
      if ("image/jpg".equals(contentType)) {
        contentType = "image/jpeg";
      }
      // Replace previous photo of same type (single active photo)
      attachments.deleteByOrganizationIdAndStudentIdAndAttachmentType(
          scope.organizationId(), studentId, type);
    } else if (!DOCUMENT_TYPES.contains(type)) {
      throw new StudentException("VALIDATION", "Unsupported attachment type: " + type);
    }

    StudentAttachmentEntity entity = new StudentAttachmentEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setStudentId(studentId);
    entity.setAdmissionNo(student.getAdmissionNo());
    entity.setAttachmentType(type);
    entity.setFileName(fileName);
    entity.setContentType(contentType);
    entity.setContentBase64(b64);
    entity.setByteLength(bytes.length);
    entity.setCreatedBy(scope.userId());
    entity.setCreatedAt(Instant.now());
    entity.setMeta(new LinkedHashMap<>());
    StudentAttachmentEntity saved = attachments.save(entity);

    if (TYPE_STUDENT_PHOTO.equals(type)) {
      Map<String, Object> answers =
          student.getAnswers() != null
              ? new LinkedHashMap<>(student.getAnswers())
              : new LinkedHashMap<>();
      answers.put("photoUrl", "/api/student/attachments/" + saved.getId() + "/content");
      answers.put("studentPhoto", saved.getId().toString());
      student.setAnswers(answers);
      student.setUpdatedAt(Instant.now());
      students.save(student);
    }

    return toDto(saved);
  }

  @Transactional
  public void delete(UUID attachmentId) {
    TenantScope scope = TenantContext.require();
    requireStaffWrite(scope);
    StudentAttachmentEntity entity =
        attachments
            .findByIdAndOrganizationId(attachmentId, scope.organizationId())
            .orElseThrow(() -> new StudentException("NOT_FOUND", "Attachment not found"));
    StudentRecordEntity student = requireStudent(entity.getStudentId(), scope.organizationId());
    requireBranch(scope, student);
    attachments.delete(entity);
    if (TYPE_STUDENT_PHOTO.equals(entity.getAttachmentType())) {
      Map<String, Object> answers =
          student.getAnswers() != null
              ? new LinkedHashMap<>(student.getAnswers())
              : new LinkedHashMap<>();
      String photoUrl = String.valueOf(answers.getOrDefault("photoUrl", ""));
      if (photoUrl.contains(attachmentId.toString())) {
        answers.remove("photoUrl");
        answers.remove("studentPhoto");
        student.setAnswers(answers);
        student.setUpdatedAt(Instant.now());
        students.save(student);
      }
    }
  }

  @Transactional(readOnly = true)
  public byte[] contentBytes(UUID attachmentId) {
    TenantScope scope = TenantContext.require();
    StudentAttachmentEntity entity =
        attachments
            .findByIdAndOrganizationId(attachmentId, scope.organizationId())
            .orElseThrow(() -> new StudentException("NOT_FOUND", "Attachment not found"));
    StudentRecordEntity student = requireStudent(entity.getStudentId(), scope.organizationId());
    requireBranch(scope, student);
    try {
      return Base64.getDecoder().decode(entity.getContentBase64());
    } catch (IllegalArgumentException ex) {
      throw new StudentException("VALIDATION", "Corrupt attachment content");
    }
  }

  @Transactional(readOnly = true)
  public StudentAttachmentEntity require(UUID attachmentId) {
    TenantScope scope = TenantContext.require();
    return attachments
        .findByIdAndOrganizationId(attachmentId, scope.organizationId())
        .orElseThrow(() -> new StudentException("NOT_FOUND", "Attachment not found"));
  }

  private Map<String, Object> toDto(StudentAttachmentEntity e) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", e.getId().toString());
    out.put("studentId", e.getStudentId().toString());
    out.put("admissionNo", e.getAdmissionNo());
    out.put("attachmentType", e.getAttachmentType());
    out.put("fileName", e.getFileName());
    out.put("contentType", e.getContentType());
    out.put("byteLength", e.getByteLength());
    out.put("createdBy", e.getCreatedBy());
    out.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
    out.put("contentUrl", "/api/student/attachments/" + e.getId() + "/content");
    out.put("photo", PHOTO_TYPES.contains(e.getAttachmentType()));
    return out;
  }

  private String normalizeType(String raw) {
    String t = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    if (PHOTO_TYPES.contains(t) || DOCUMENT_TYPES.contains(t)) {
      return t;
    }
    throw new StudentException(
        "VALIDATION",
        "Supported types: STUDENT_PHOTO, FATHER_PHOTO, MOTHER_PHOTO, GUARDIAN_PHOTO, "
            + "BIRTH_CERTIFICATE, TRANSFER_CERTIFICATE, PREVIOUS_MARKSHEET, AADHAAR_COPY, "
            + "INCOME_CERTIFICATE, CASTE_CERTIFICATE, MEDICAL_CERTIFICATE, PASSPORT_PHOTO, OTHER");
  }

  private boolean looksLikeImageName(String name) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
  }

  private StudentRecordEntity requireStudent(UUID id, String org) {
    return students
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new StudentException("NOT_FOUND", "Student not found"));
  }

  private void requireBranch(TenantScope scope, StudentRecordEntity student) {
    try {
      BranchAccess.requireEntityBranch(scope, student.getBranchId());
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
  }

  private void requireStaffWrite(TenantScope scope) {
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new StudentException("FORBIDDEN", ex.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> moduleSettings(TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, "student");
    Object settings = module != null ? module.get("settings") : null;
    if (settings instanceof Map<?, ?> m) {
      return new LinkedHashMap<>((Map<String, Object>) m);
    }
    return Map.of();
  }

  private static boolean bool(Object v, boolean def) {
    if (v == null) return def;
    if (v instanceof Boolean b) return b;
    return Boolean.parseBoolean(String.valueOf(v));
  }

  private static int intOr(Object v, int def) {
    if (v == null) return def;
    if (v instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException ex) {
      return def;
    }
  }
}
