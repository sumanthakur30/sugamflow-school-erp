package com.sugamflow.school.admission.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admission_lead")
public class AdmissionLeadEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false, length = 64)
  private String organizationId;

  @Column(name = "student_name", nullable = false, length = 160)
  private String studentName;

  @Column(name = "father_name", length = 160)
  private String fatherName;

  @Column(name = "mother_name", length = 160)
  private String motherName;

  @Column(nullable = false, length = 32)
  private String phone;

  @Column(name = "father_phone", length = 32)
  private String fatherPhone;

  @Column(name = "mother_phone", length = 32)
  private String motherPhone;

  @Column(length = 400)
  private String address;

  @Column(name = "class_applied_for", length = 80)
  private String classAppliedFor;

  @Column(name = "admission_no", length = 64)
  private String admissionNo;

  @Column(name = "created_by", length = 160)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(length = 1000)
  private String remark;

  @Column(name = "assigned_to", length = 160)
  private String assignedTo;

  @Column(name = "example_seed", nullable = false)
  private boolean exampleSeed;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

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

  public String getStudentName() {
    return studentName;
  }

  public void setStudentName(String studentName) {
    this.studentName = studentName;
  }

  public String getFatherName() {
    return fatherName;
  }

  public void setFatherName(String fatherName) {
    this.fatherName = fatherName;
  }

  public String getMotherName() {
    return motherName;
  }

  public void setMotherName(String motherName) {
    this.motherName = motherName;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getFatherPhone() {
    return fatherPhone;
  }

  public void setFatherPhone(String fatherPhone) {
    this.fatherPhone = fatherPhone;
  }

  public String getMotherPhone() {
    return motherPhone;
  }

  public void setMotherPhone(String motherPhone) {
    this.motherPhone = motherPhone;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getClassAppliedFor() {
    return classAppliedFor;
  }

  public void setClassAppliedFor(String classAppliedFor) {
    this.classAppliedFor = classAppliedFor;
  }

  public String getAdmissionNo() {
    return admissionNo;
  }

  public void setAdmissionNo(String admissionNo) {
    this.admissionNo = admissionNo;
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

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public void setScheduledAt(Instant scheduledAt) {
    this.scheduledAt = scheduledAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public String getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(String assignedTo) {
    this.assignedTo = assignedTo;
  }

  public boolean isExampleSeed() {
    return exampleSeed;
  }

  public void setExampleSeed(boolean exampleSeed) {
    this.exampleSeed = exampleSeed;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
