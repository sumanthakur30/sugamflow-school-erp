package com.sugamflow.school.exam.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exam_mark")
public class ExamMarkEntity {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "exam_definition_id", nullable = false)
  private UUID examDefinitionId;

  @Column(name = "student_id")
  private UUID studentId;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "student_name", length = 191)
  private String studentName;

  @Column(name = "marks_obtained", precision = 10, scale = 2)
  private BigDecimal marksObtained;

  @Column(length = 32)
  private String grade;

  @Column(name = "entry_status", length = 32)
  private String entryStatus;

  @Column(length = 500)
  private String remarks;

  @Column(name = "roll_no", length = 64)
  private String rollNo;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "component_marks", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> componentMarks = new LinkedHashMap<>();

  @Column(name = "updated_by", length = 128)
  private String updatedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public UUID getExamDefinitionId() { return examDefinitionId; }
  public void setExamDefinitionId(UUID examDefinitionId) { this.examDefinitionId = examDefinitionId; }
  public UUID getStudentId() { return studentId; }
  public void setStudentId(UUID studentId) { this.studentId = studentId; }
  public String getAdmissionNo() { return admissionNo; }
  public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
  public String getStudentName() { return studentName; }
  public void setStudentName(String studentName) { this.studentName = studentName; }
  public BigDecimal getMarksObtained() { return marksObtained; }
  public void setMarksObtained(BigDecimal marksObtained) { this.marksObtained = marksObtained; }
  public String getGrade() { return grade; }
  public void setGrade(String grade) { this.grade = grade; }
  public String getEntryStatus() { return entryStatus; }
  public void setEntryStatus(String entryStatus) { this.entryStatus = entryStatus; }
  public String getRemarks() { return remarks; }
  public void setRemarks(String remarks) { this.remarks = remarks; }
  public String getRollNo() { return rollNo; }
  public void setRollNo(String rollNo) { this.rollNo = rollNo; }
  public Map<String, Object> getComponentMarks() { return componentMarks; }
  public void setComponentMarks(Map<String, Object> componentMarks) {
    this.componentMarks = componentMarks == null ? new LinkedHashMap<>() : componentMarks;
  }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
