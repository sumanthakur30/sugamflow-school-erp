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
@Table(name = "student_attachment")
public class StudentAttachmentEntity {

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

  @Column(name = "attachment_type", nullable = false, length = 64)
  private String attachmentType;

  @Column(name = "file_name", length = 255)
  private String fileName;

  @Column(name = "content_type", length = 128)
  private String contentType;

  @Column(name = "content_base64", nullable = false, columnDefinition = "TEXT")
  private String contentBase64;

  @Column(name = "byte_length")
  private Integer byteLength;

  @Column(name = "created_by", length = 128)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "meta", nullable = false, columnDefinition = "jsonb")
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

  public String getAttachmentType() {
    return attachmentType;
  }

  public void setAttachmentType(String attachmentType) {
    this.attachmentType = attachmentType;
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

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  public void setMeta(Map<String, Object> meta) {
    this.meta = meta != null ? meta : new LinkedHashMap<>();
  }
}
