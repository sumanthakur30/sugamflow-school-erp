package com.sugamflow.school.support.ticket.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.support.ticket.config.SupportTicketProperties;
import com.sugamflow.school.support.ticket.dto.AddSupportCommentRequest;
import com.sugamflow.school.support.ticket.dto.AdminSupportCommentRequest;
import com.sugamflow.school.support.ticket.dto.CreateSupportTicketRequest;
import com.sugamflow.school.support.ticket.dto.SupportAttachmentResponse;
import com.sugamflow.school.support.ticket.dto.SupportCommentResponse;
import com.sugamflow.school.support.ticket.dto.SupportTicketResponse;
import com.sugamflow.school.support.ticket.dto.UpdateSupportTicketRequest;
import com.sugamflow.school.support.ticket.model.SupportProduct;
import com.sugamflow.school.support.ticket.model.SupportTicket;
import com.sugamflow.school.support.ticket.model.SupportTicketAttachment;
import com.sugamflow.school.support.ticket.model.SupportTicketAuditLog;
import com.sugamflow.school.support.ticket.model.SupportTicketComment;
import com.sugamflow.school.support.ticket.model.SupportTicketPriority;
import com.sugamflow.school.support.ticket.model.SupportTicketStatus;
import com.sugamflow.school.support.ticket.repository.SupportTicketAttachmentRepository;
import com.sugamflow.school.support.ticket.repository.SupportTicketAuditLogRepository;
import com.sugamflow.school.support.ticket.repository.SupportTicketCommentRepository;
import com.sugamflow.school.support.ticket.repository.SupportTicketRepository;
import com.sugamflow.school.support.ticket.support.SupportAdminAuthorization;

@Service
public class SupportTicketService {
  private static final DateTimeFormatter TICKET_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final SupportTicketRepository ticketRepository;
  private final SupportTicketAttachmentRepository attachmentRepository;
  private final SupportTicketCommentRepository commentRepository;
  private final SupportTicketAuditLogRepository auditLogRepository;
  private final SupportAttachmentStorageService storageService;
  private final SupportTicketProperties properties;

  public SupportTicketService(
      SupportTicketRepository ticketRepository,
      SupportTicketAttachmentRepository attachmentRepository,
      SupportTicketCommentRepository commentRepository,
      SupportTicketAuditLogRepository auditLogRepository,
      SupportAttachmentStorageService storageService,
      SupportTicketProperties properties) {
    this.ticketRepository = ticketRepository;
    this.attachmentRepository = attachmentRepository;
    this.commentRepository = commentRepository;
    this.auditLogRepository = auditLogRepository;
    this.storageService = storageService;
    this.properties = properties;
  }

  @Transactional
  public SupportTicketResponse createTicket(
      CreateSupportTicketRequest request, List<MultipartFile> attachments) throws IOException {
    TenantScope scope = TenantContext.require();
    String organizationId = scope.organizationId();
    String actor = currentActor(scope);

    SupportProduct product =
        request.product() != null ? request.product() : SupportProduct.SCHOOL;
    SupportTicket ticket = new SupportTicket();
    ticket.setProduct(product);
    ticket.setOrganizationId(organizationId);
    ticket.setShopId(organizationId);
    ticket.setIssueType(request.issueType());
    ticket.setSubject(request.subject().trim());
    ticket.setDescription(request.description().trim());
    ticket.setPriority(
        request.priority() != null ? request.priority() : SupportTicketPriority.MEDIUM);
    ticket.setStatus(SupportTicketStatus.OPEN);
    ticket.setContactEmail(trimToNull(request.contactEmail()));
    ticket.setContactMobile(trimToNull(request.contactMobile()));
    ticket.setModuleName(trimToNull(request.moduleName()));
    ticket.setAppVersion(trimToNull(request.appVersion()));
    ticket.setDeviceInfo(trimToNull(request.deviceInfo()));
    ticket.setSubmittedBy(actor);
    ticket.setSlaDueAt(calculateSlaDue(ticket.getPriority()));
    ticket.setTicketNumber("TMP" + System.currentTimeMillis());
    ticket = ticketRepository.save(ticket);
    ticket.setTicketNumber(generateTicketNumber(product, ticket.getId()));
    ticket = ticketRepository.save(ticket);

    audit(ticket.getId(), "CREATED", null, null, null, actor);

    if (attachments != null) {
      storeAttachments(ticket, attachments, actor);
    }

    return loadTicketResponse(ticket);
  }

  @Transactional(readOnly = true)
  public Page<SupportTicketResponse> listMyTickets(Pageable pageable) {
    TenantScope scope = TenantContext.require();
    String organizationId = scope.organizationId();
    SupportProduct product = resolveProduct();
    return ticketRepository
        .findByProductAndOrganizationIdAndShopIdOrderByCreatedAtDesc(
            product, organizationId, organizationId, pageable)
        .map(SupportTicketResponse::summary);
  }

  @Transactional(readOnly = true)
  public SupportTicketResponse getMyTicket(Long ticketId) {
    SupportTicket ticket = requireOrgTicket(ticketId);
    return loadTicketResponse(ticket);
  }

  @Transactional
  public SupportTicketResponse updateMyTicket(Long ticketId, UpdateSupportTicketRequest request) {
    SupportTicket ticket = requireOrgTicket(ticketId);
    requireEditableTicket(ticket);
    String actor = currentActor(TenantContext.require());
    boolean changed = false;

    if (request.subject() != null && !request.subject().isBlank()) {
      String subject = request.subject().trim();
      if (!subject.equals(ticket.getSubject())) {
        audit(ticketId, "UPDATED", "subject", ticket.getSubject(), subject, actor);
        ticket.setSubject(subject);
        changed = true;
      }
    }
    if (request.additionalDescription() != null && !request.additionalDescription().isBlank()) {
      String addition = request.additionalDescription().trim();
      String separator =
          "\n\n--- Update "
              + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
              + " ---\n";
      String updated =
          (ticket.getDescription() == null || ticket.getDescription().isBlank())
              ? addition
              : ticket.getDescription() + separator + addition;
      if (updated.length() > 10000) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description exceeds maximum length");
      }
      audit(ticketId, "UPDATED", "description", null, "additional details added", actor);
      ticket.setDescription(updated);
      changed = true;
    }
    if (request.moduleName() != null) {
      String moduleName = trimToNull(request.moduleName());
      String current = ticket.getModuleName();
      if ((moduleName == null && current != null) || (moduleName != null && !moduleName.equals(current))) {
        audit(ticketId, "UPDATED", "moduleName", current, moduleName, actor);
        ticket.setModuleName(moduleName);
        changed = true;
      }
    }
    if (request.contactEmail() != null) {
      String contactEmail = trimToNull(request.contactEmail());
      String current = ticket.getContactEmail();
      if ((contactEmail == null && current != null)
          || (contactEmail != null && !contactEmail.equals(current))) {
        audit(ticketId, "UPDATED", "contactEmail", current, contactEmail, actor);
        ticket.setContactEmail(contactEmail);
        changed = true;
      }
    }
    if (request.contactMobile() != null) {
      String contactMobile = trimToNull(request.contactMobile());
      String current = ticket.getContactMobile();
      if ((contactMobile == null && current != null)
          || (contactMobile != null && !contactMobile.equals(current))) {
        audit(ticketId, "UPDATED", "contactMobile", current, contactMobile, actor);
        ticket.setContactMobile(contactMobile);
        changed = true;
      }
    }
    if (!changed) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No changes to save");
    }
    ticketRepository.save(ticket);
    return loadTicketResponse(ticket);
  }

  @Transactional
  public SupportTicketResponse closeMyTicket(Long ticketId) {
    SupportTicket ticket = requireOrgTicket(ticketId);
    if (ticket.getStatus() == SupportTicketStatus.CLOSED
        || ticket.getStatus() == SupportTicketStatus.REJECTED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket is already closed");
    }
    String actor = currentActor(TenantContext.require());
    SupportTicketStatus old = ticket.getStatus();
    ticket.setStatus(SupportTicketStatus.CLOSED);
    ticket.setClosedAt(LocalDateTime.now());
    ticketRepository.save(ticket);
    audit(ticketId, "STATUS_CHANGE", "status", old.name(), SupportTicketStatus.CLOSED.name(), actor);
    return loadTicketResponse(ticket);
  }

  @Transactional
  public SupportTicketResponse addCustomerComment(Long ticketId, AddSupportCommentRequest request) {
    SupportTicket ticket = requireOrgTicket(ticketId);
    if (ticket.getStatus() == SupportTicketStatus.CLOSED
        || ticket.getStatus() == SupportTicketStatus.REJECTED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket is closed");
    }
    String actor = currentActor(TenantContext.require());
    SupportTicketComment comment = new SupportTicketComment();
    comment.setTicketId(ticketId);
    comment.setBody(request.body().trim());
    comment.setAuthor(actor);
    comment.setStaffResponse(false);
    comment.setInternalNote(false);
    commentRepository.save(comment);
    audit(ticketId, "CUSTOMER_COMMENT", null, null, null, actor);
    return loadTicketResponse(ticket);
  }

  @Transactional
  public SupportAttachmentResponse addAttachment(Long ticketId, MultipartFile file) throws IOException {
    SupportTicket ticket = requireOrgTicket(ticketId);
    requireEditableTicket(ticket);
    validateAttachmentLimit(ticketId);
    return storeSingleAttachment(ticket, file, currentActor(TenantContext.require()));
  }

  @Transactional(readOnly = true)
  public SupportTicketAttachment requireAttachment(Long ticketId, Long attachmentId) {
    SupportTicket ticket = requireOrgTicket(ticketId);
    return attachmentRepository
        .findByIdAndTicketId(attachmentId, ticket.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
  }

  @Transactional(readOnly = true)
  public Page<SupportTicketResponse> listAllTickets(
      SupportTicketStatus status, SupportProduct product, Pageable pageable) {
    SupportAdminAuthorization.requireAdminPermission();
    if (product != null && status != null) {
      return ticketRepository
          .findByProductAndStatusOrderByCreatedAtDesc(product, status, pageable)
          .map(SupportTicketResponse::summary);
    }
    if (product != null) {
      return ticketRepository
          .findByProductOrderByCreatedAtDesc(product, pageable)
          .map(SupportTicketResponse::summary);
    }
    if (status != null) {
      return ticketRepository
          .findByStatusOrderByCreatedAtDesc(status, pageable)
          .map(SupportTicketResponse::summary);
    }
    return ticketRepository.findAllByOrderByCreatedAtDesc(pageable).map(SupportTicketResponse::summary);
  }

  @Transactional(readOnly = true)
  public SupportTicketResponse getAdminTicket(Long ticketId) {
    SupportAdminAuthorization.requireAdminPermission();
    SupportTicket ticket =
        ticketRepository
            .findById(ticketId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    return loadTicketResponse(ticket, true);
  }

  @Transactional
  public SupportTicketResponse updateStatus(Long ticketId, SupportTicketStatus newStatus) {
    SupportAdminAuthorization.requireAdminPermission();
    SupportTicket ticket = requireTicket(ticketId);
    SupportTicketStatus old = ticket.getStatus();
    ticket.setStatus(newStatus);
    applyStatusTimestamps(ticket, newStatus);
    ticketRepository.save(ticket);
    audit(
        ticketId,
        "STATUS_CHANGE",
        "status",
        old.name(),
        newStatus.name(),
        SupportAdminAuthorization.currentActor());
    return loadTicketResponse(ticket, true);
  }

  @Transactional
  public SupportTicketResponse updatePriority(Long ticketId, SupportTicketPriority priority) {
    SupportAdminAuthorization.requireAdminPermission();
    SupportTicket ticket = requireTicket(ticketId);
    SupportTicketPriority old = ticket.getPriority();
    ticket.setPriority(priority);
    ticket.setSlaDueAt(calculateSlaDue(priority));
    ticketRepository.save(ticket);
    audit(
        ticketId,
        "PRIORITY_CHANGE",
        "priority",
        old.name(),
        priority.name(),
        SupportAdminAuthorization.currentActor());
    return loadTicketResponse(ticket, true);
  }

  @Transactional
  public SupportTicketResponse assignTicket(Long ticketId, String assignedTo) {
    SupportAdminAuthorization.requireAdminPermission();
    SupportTicket ticket = requireTicket(ticketId);
    String old = ticket.getAssignedTo();
    String assignee = assignedTo.trim();
    ticket.setAssignedTo(assignee);
    if (ticket.getStatus() == SupportTicketStatus.OPEN) {
      ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
    }
    ticketRepository.save(ticket);
    audit(ticketId, "ASSIGNED", "assignedTo", old, assignee, SupportAdminAuthorization.currentActor());
    return loadTicketResponse(ticket, true);
  }

  @Transactional
  public SupportTicketResponse addAdminComment(Long ticketId, AdminSupportCommentRequest request) {
    SupportAdminAuthorization.requireAdminPermission();
    SupportTicket ticket = requireTicket(ticketId);
    String actor = SupportAdminAuthorization.currentActor();
    SupportTicketComment comment = new SupportTicketComment();
    comment.setTicketId(ticketId);
    comment.setBody(request.body().trim());
    comment.setAuthor(actor);
    comment.setStaffResponse(!request.internalNote());
    comment.setInternalNote(request.internalNote());
    commentRepository.save(comment);

    if (!request.internalNote()) {
      if (ticket.getFirstResponseAt() == null) {
        ticket.setFirstResponseAt(LocalDateTime.now());
      }
      if (ticket.getStatus() == SupportTicketStatus.OPEN) {
        ticket.setStatus(SupportTicketStatus.IN_PROGRESS);
      }
      ticketRepository.save(ticket);
    }
    audit(
        ticketId,
        request.internalNote() ? "INTERNAL_NOTE" : "STAFF_RESPONSE",
        null,
        null,
        null,
        actor);
    return loadTicketResponse(ticket, true);
  }

  private SupportTicket requireTicket(Long ticketId) {
    return ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
  }

  private static void applyStatusTimestamps(SupportTicket ticket, SupportTicketStatus status) {
    LocalDateTime now = LocalDateTime.now();
    if (status == SupportTicketStatus.RESOLVED && ticket.getResolvedAt() == null) {
      ticket.setResolvedAt(now);
    }
    if ((status == SupportTicketStatus.CLOSED || status == SupportTicketStatus.REJECTED)
        && ticket.getClosedAt() == null) {
      ticket.setClosedAt(now);
    }
    if (status == SupportTicketStatus.IN_PROGRESS && ticket.getFirstResponseAt() == null) {
      ticket.setFirstResponseAt(now);
    }
  }

  private static void requireEditableTicket(SupportTicket ticket) {
    if (ticket.getStatus() == SupportTicketStatus.CLOSED
        || ticket.getStatus() == SupportTicketStatus.REJECTED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket is closed");
    }
  }

  private SupportTicket requireOrgTicket(Long ticketId) {
    TenantScope scope = TenantContext.require();
    String organizationId = scope.organizationId();
    SupportProduct product = resolveProduct();
    return ticketRepository
        .findByIdAndProductAndOrganizationIdAndShopId(
            ticketId, product, organizationId, organizationId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
  }

  /** Default SCHOOL; shop clients send X-Product: SHOP (or body product on create). */
  private static SupportProduct resolveProduct() {
    // Body product is set on create; list/get use header when present.
    String header = org.springframework.web.context.request.RequestContextHolder
            .getRequestAttributes()
        instanceof org.springframework.web.context.request.ServletRequestAttributes attrs
        ? attrs.getRequest().getHeader("X-Product")
        : null;
    if (header != null && !header.isBlank()) {
      try {
        return SupportProduct.valueOf(header.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
        // fall through
      }
    }
    return SupportProduct.SCHOOL;
  }

  private String generateTicketNumber(SupportProduct product, Long id) {
    String day = LocalDateTime.now().format(TICKET_DAY);
    String prefix = product == SupportProduct.SHOP ? "SF" : "SCH";
    return prefix + "-" + day + "-" + String.format("%06d", id);
  }

  private SupportTicketResponse loadTicketResponse(SupportTicket ticket) {
    return loadTicketResponse(ticket, false);
  }

  private SupportTicketResponse loadTicketResponse(SupportTicket ticket, boolean includeInternal) {
    List<SupportAttachmentResponse> attachments =
        attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()).stream()
            .map(SupportAttachmentResponse::from)
            .toList();
    List<SupportCommentResponse> comments =
        (includeInternal
                ? commentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                : commentRepository.findByTicketIdAndInternalNoteFalseOrderByCreatedAtAsc(
                    ticket.getId()))
            .stream()
            .map(SupportCommentResponse::from)
            .toList();
    return SupportTicketResponse.from(ticket, attachments, comments);
  }

  private void storeAttachments(SupportTicket ticket, List<MultipartFile> attachments, String actor)
      throws IOException {
    if (attachments == null || attachments.isEmpty()) {
      return;
    }
    long existing = attachmentRepository.countByTicketId(ticket.getId());
    if (existing + attachments.size() > properties.getMaxAttachmentsPerTicket()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Maximum " + properties.getMaxAttachmentsPerTicket() + " attachments per ticket");
    }
    for (MultipartFile file : attachments) {
      if (file != null && !file.isEmpty()) {
        storeSingleAttachment(ticket, file, actor);
      }
    }
  }

  private SupportAttachmentResponse storeSingleAttachment(
      SupportTicket ticket, MultipartFile file, String actor) throws IOException {
    validateAttachmentLimit(ticket.getId());
    var stored =
        storageService.store(
            ticket.getOrganizationId(), ticket.getShopId(), ticket.getId(), file);
    SupportTicketAttachment attachment = new SupportTicketAttachment();
    attachment.setTicketId(ticket.getId());
    attachment.setFileName(stored.fileName());
    attachment.setContentType(stored.contentType());
    attachment.setFileSize(stored.fileSize());
    attachment.setStoragePath(stored.storagePath());
    attachment.setUploadedBy(actor);
    attachment = attachmentRepository.save(attachment);
    audit(ticket.getId(), "ATTACHMENT_ADDED", "attachment", null, attachment.getFileName(), actor);
    return SupportAttachmentResponse.from(attachment);
  }

  private void validateAttachmentLimit(Long ticketId) {
    if (attachmentRepository.countByTicketId(ticketId) >= properties.getMaxAttachmentsPerTicket()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Maximum attachments reached for this ticket");
    }
  }

  private void audit(
      Long ticketId, String action, String field, String oldValue, String newValue, String actor) {
    SupportTicketAuditLog log = new SupportTicketAuditLog();
    log.setTicketId(ticketId);
    log.setAction(action);
    log.setFieldName(field);
    log.setOldValue(oldValue);
    log.setNewValue(newValue);
    log.setActor(actor);
    auditLogRepository.save(log);
  }

  private static LocalDateTime calculateSlaDue(SupportTicketPriority priority) {
    int hours =
        switch (priority) {
          case CRITICAL -> 4;
          case HIGH -> 24;
          case MEDIUM -> 48;
          case LOW -> 72;
        };
    return LocalDateTime.now().plusHours(hours);
  }

  private static String currentActor(TenantScope scope) {
    if (scope.userId() != null && !scope.userId().isBlank()) {
      return scope.userId();
    }
    return "anonymous";
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
