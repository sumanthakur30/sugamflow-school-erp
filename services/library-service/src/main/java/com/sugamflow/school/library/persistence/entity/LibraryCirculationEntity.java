package com.sugamflow.school.library.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "library_circulation")
public class LibraryCirculationEntity {
  @Id private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "branch_id", length = 64)
  private String branchId;

  @Column(name = "book_id", nullable = false)
  private UUID bookId;

  @Column(name = "student_id")
  private UUID studentId;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "student_name", length = 191)
  private String studentName;

  @Column(name = "class_section", length = 128)
  private String classSection;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt = Instant.now();

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(name = "returned_at")
  private Instant returnedAt;

  @Column(name = "fine_amount")
  private BigDecimal fineAmount;

  @Column(name = "created_by", length = 128)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public UUID getBookId() { return bookId; }
  public void setBookId(UUID bookId) { this.bookId = bookId; }
  public UUID getStudentId() { return studentId; }
  public void setStudentId(UUID studentId) { this.studentId = studentId; }
  public String getAdmissionNo() { return admissionNo; }
  public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
  public String getStudentName() { return studentName; }
  public void setStudentName(String studentName) { this.studentName = studentName; }
  public String getClassSection() { return classSection; }
  public void setClassSection(String classSection) { this.classSection = classSection; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getIssuedAt() { return issuedAt; }
  public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
  public Instant getDueAt() { return dueAt; }
  public void setDueAt(Instant dueAt) { this.dueAt = dueAt; }
  public Instant getReturnedAt() { return returnedAt; }
  public void setReturnedAt(Instant returnedAt) { this.returnedAt = returnedAt; }
  public BigDecimal getFineAmount() { return fineAmount; }
  public void setFineAmount(BigDecimal fineAmount) { this.fineAmount = fineAmount; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
