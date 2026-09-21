package com.sugamflow.school.compliance.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "school_compliance_profile")
public class SchoolComplianceProfileEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false, unique = true, length = 100)
  private String organizationId;

  @Column(name = "board_code", nullable = false, length = 40)
  private String boardCode = "CBSE";

  @Column(name = "active_pack_key", length = 80)
  private String activePackKey;

  @Column(name = "school_name")
  private String schoolName;

  @Column(name = "affiliation_number", length = 80)
  private String affiliationNumber;

  @Column(name = "school_code", length = 80)
  private String schoolCode;

  @Column(name = "udise_plus", length = 80)
  private String udisePlus;

  @Column(name = "dise_code", length = 80)
  private String diseCode;

  @Column(name = "address_line", columnDefinition = "text")
  private String addressLine;

  @Column(length = 120)
  private String city;

  @Column(name = "state_code", length = 40)
  private String stateCode;

  @Column(length = 20)
  private String pincode;

  @Column(name = "principal_name", length = 160)
  private String principalName;

  @Column(name = "principal_mobile", length = 30)
  private String principalMobile;

  @Column(name = "principal_email", length = 160)
  private String principalEmail;

  @Column(name = "school_phone", length = 30)
  private String schoolPhone;

  @Column(name = "school_email", length = 160)
  private String schoolEmail;

  @Column(name = "bank_account_name", length = 160)
  private String bankAccountName;

  @Column(name = "bank_account_number", length = 64)
  private String bankAccountNumber;

  @Column(name = "bank_ifsc", length = 20)
  private String bankIfsc;

  @Column(name = "trust_society_name")
  private String trustSocietyName;

  @Column(name = "recognition_details", columnDefinition = "text")
  private String recognitionDetails;

  @Column(name = "infrastructure_notes", columnDefinition = "text")
  private String infrastructureNotes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getBoardCode() {
    return boardCode;
  }

  public void setBoardCode(String boardCode) {
    this.boardCode = boardCode;
  }

  public String getActivePackKey() {
    return activePackKey;
  }

  public void setActivePackKey(String activePackKey) {
    this.activePackKey = activePackKey;
  }

  public String getSchoolName() {
    return schoolName;
  }

  public void setSchoolName(String schoolName) {
    this.schoolName = schoolName;
  }

  public String getAffiliationNumber() {
    return affiliationNumber;
  }

  public void setAffiliationNumber(String affiliationNumber) {
    this.affiliationNumber = affiliationNumber;
  }

  public String getSchoolCode() {
    return schoolCode;
  }

  public void setSchoolCode(String schoolCode) {
    this.schoolCode = schoolCode;
  }

  public String getUdisePlus() {
    return udisePlus;
  }

  public void setUdisePlus(String udisePlus) {
    this.udisePlus = udisePlus;
  }

  public String getDiseCode() {
    return diseCode;
  }

  public void setDiseCode(String diseCode) {
    this.diseCode = diseCode;
  }

  public String getAddressLine() {
    return addressLine;
  }

  public void setAddressLine(String addressLine) {
    this.addressLine = addressLine;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getStateCode() {
    return stateCode;
  }

  public void setStateCode(String stateCode) {
    this.stateCode = stateCode;
  }

  public String getPincode() {
    return pincode;
  }

  public void setPincode(String pincode) {
    this.pincode = pincode;
  }

  public String getPrincipalName() {
    return principalName;
  }

  public void setPrincipalName(String principalName) {
    this.principalName = principalName;
  }

  public String getPrincipalMobile() {
    return principalMobile;
  }

  public void setPrincipalMobile(String principalMobile) {
    this.principalMobile = principalMobile;
  }

  public String getPrincipalEmail() {
    return principalEmail;
  }

  public void setPrincipalEmail(String principalEmail) {
    this.principalEmail = principalEmail;
  }

  public String getSchoolPhone() {
    return schoolPhone;
  }

  public void setSchoolPhone(String schoolPhone) {
    this.schoolPhone = schoolPhone;
  }

  public String getSchoolEmail() {
    return schoolEmail;
  }

  public void setSchoolEmail(String schoolEmail) {
    this.schoolEmail = schoolEmail;
  }

  public String getBankAccountName() {
    return bankAccountName;
  }

  public void setBankAccountName(String bankAccountName) {
    this.bankAccountName = bankAccountName;
  }

  public String getBankAccountNumber() {
    return bankAccountNumber;
  }

  public void setBankAccountNumber(String bankAccountNumber) {
    this.bankAccountNumber = bankAccountNumber;
  }

  public String getBankIfsc() {
    return bankIfsc;
  }

  public void setBankIfsc(String bankIfsc) {
    this.bankIfsc = bankIfsc;
  }

  public String getTrustSocietyName() {
    return trustSocietyName;
  }

  public void setTrustSocietyName(String trustSocietyName) {
    this.trustSocietyName = trustSocietyName;
  }

  public String getRecognitionDetails() {
    return recognitionDetails;
  }

  public void setRecognitionDetails(String recognitionDetails) {
    this.recognitionDetails = recognitionDetails;
  }

  public String getInfrastructureNotes() {
    return infrastructureNotes;
  }

  public void setInfrastructureNotes(String infrastructureNotes) {
    this.infrastructureNotes = infrastructureNotes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
