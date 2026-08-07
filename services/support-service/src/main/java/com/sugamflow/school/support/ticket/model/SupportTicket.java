package com.sugamflow.school.support.ticket.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ticket_number", nullable = false, unique = true, length = 30)
  private String ticketNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "product", nullable = false, length = 20)
  private SupportProduct product = SupportProduct.SCHOOL;

  @Column(name = "organization_id", nullable = false, length = 100)
  private String organizationId;

  @Column(name = "shop_id", nullable = false, length = 100)
  private String shopId;

  @Enumerated(EnumType.STRING)
  @Column(name = "issue_type", nullable = false, length = 40)
  private SupportIssueType issueType;

  @Column(nullable = false, length = 255)
  private String subject;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SupportTicketPriority priority = SupportTicketPriority.MEDIUM;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SupportTicketStatus status = SupportTicketStatus.OPEN;

  @Column(name = "contact_email", length = 255)
  private String contactEmail;

  @Column(name = "contact_mobile", length = 20)
  private String contactMobile;

  @Column(name = "module_name", length = 100)
  private String moduleName;

  @Column(name = "app_version", length = 50)
  private String appVersion;

  @Column(name = "device_info", columnDefinition = "text")
  private String deviceInfo;

  @Column(name = "submitted_by", length = 255)
  private String submittedBy;

  @Column(name = "assigned_to", length = 255)
  private String assignedTo;

  @Column(name = "sla_due_at")
  private LocalDateTime slaDueAt;

  @Column(name = "first_response_at")
  private LocalDateTime firstResponseAt;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTicketNumber() {
    return ticketNumber;
  }

  public void setTicketNumber(String ticketNumber) {
    this.ticketNumber = ticketNumber;
  }

  public SupportProduct getProduct() {
    return product;
  }

  public void setProduct(SupportProduct product) {
    this.product = product;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getShopId() {
    return shopId;
  }

  public void setShopId(String shopId) {
    this.shopId = shopId;
  }

  public SupportIssueType getIssueType() {
    return issueType;
  }

  public void setIssueType(SupportIssueType issueType) {
    this.issueType = issueType;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public SupportTicketPriority getPriority() {
    return priority;
  }

  public void setPriority(SupportTicketPriority priority) {
    this.priority = priority;
  }

  public SupportTicketStatus getStatus() {
    return status;
  }

  public void setStatus(SupportTicketStatus status) {
    this.status = status;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public void setContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
  }

  public String getContactMobile() {
    return contactMobile;
  }

  public void setContactMobile(String contactMobile) {
    this.contactMobile = contactMobile;
  }

  public String getModuleName() {
    return moduleName;
  }

  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public void setAppVersion(String appVersion) {
    this.appVersion = appVersion;
  }

  public String getDeviceInfo() {
    return deviceInfo;
  }

  public void setDeviceInfo(String deviceInfo) {
    this.deviceInfo = deviceInfo;
  }

  public String getSubmittedBy() {
    return submittedBy;
  }

  public void setSubmittedBy(String submittedBy) {
    this.submittedBy = submittedBy;
  }

  public String getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(String assignedTo) {
    this.assignedTo = assignedTo;
  }

  public LocalDateTime getSlaDueAt() {
    return slaDueAt;
  }

  public void setSlaDueAt(LocalDateTime slaDueAt) {
    this.slaDueAt = slaDueAt;
  }

  public LocalDateTime getFirstResponseAt() {
    return firstResponseAt;
  }

  public void setFirstResponseAt(LocalDateTime firstResponseAt) {
    this.firstResponseAt = firstResponseAt;
  }

  public LocalDateTime getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(LocalDateTime resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public LocalDateTime getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(LocalDateTime closedAt) {
    this.closedAt = closedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
