package com.sugamflow.school.student.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "student_document")
public class StudentDocumentEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "academic_session_id", length = 64)
  private String academicSessionId;

  @Column(name = "student_id", nullable = false)
  private UUID studentId;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "document_type", nullable = false, length = 64)
  private String documentType;

  @Column(name = "template_key", nullable = false, length = 128)
  private String templateKey;

  @Column(name = "reference_no", nullable = false, length = 64)
  private String referenceNo;

  @Column(name = "verification_token", nullable = false, length = 64)
  private String verificationToken;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "file_name", length = 255)
  private String fileName;

  @Column(name = "content_type", length = 128)
  private String contentType;

  @Column(name = "content_base64", columnDefinition = "text")
  private String contentBase64;

  @Column(name = "byte_length")
  private Integer byteLength;

  @Column(name = "issued_by", length = 128)
  private String issuedBy;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt = Instant.now();

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoke_reason", columnDefinition = "text")
  private String revokeReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> meta = new LinkedHashMap<>();

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getBranchId() {
    return branchId;
  }

  public void setBranchId(String branchId) {
    this.branchId = branchId;
  }

  public String getAcademicSessionId() {
    return academicSessionId;
  }

  public void setAcademicSessionId(String academicSessionId) {
    this.academicSessionId = academicSessionId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public void setStudentId(UUID studentId) {
    this.studentId = studentId;
  }

  public String getAdmissionNo() {
    return admissionNo;
  }

  public void setAdmissionNo(String admissionNo) {
    this.admissionNo = admissionNo;
  }

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public void setTemplateKey(String templateKey) {
    this.templateKey = templateKey;
  }

  public String getReferenceNo() {
    return referenceNo;
  }

  public void setReferenceNo(String referenceNo) {
    this.referenceNo = referenceNo;
  }

  public String getVerificationToken() {
    return verificationToken;
  }

  public void setVerificationToken(String verificationToken) {
    this.verificationToken = verificationToken;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public String getContentBase64() {
    return contentBase64;
  }

  public void setContentBase64(String contentBase64) {
    this.contentBase64 = contentBase64;
  }

  public Integer getByteLength() {
    return byteLength;
  }

  public void setByteLength(Integer byteLength) {
    this.byteLength = byteLength;
  }

  public String getIssuedBy() {
    return issuedBy;
  }

  public void setIssuedBy(String issuedBy) {
    this.issuedBy = issuedBy;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public void setIssuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public String getRevokeReason() {
    return revokeReason;
  }

  public void setRevokeReason(String revokeReason) {
    this.revokeReason = revokeReason;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(Map<String, Object> meta) {
    this.meta = meta;
  }
}
