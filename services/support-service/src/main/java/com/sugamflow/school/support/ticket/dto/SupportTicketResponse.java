package com.sugamflow.school.support.ticket.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sugamflow.school.support.ticket.model.SupportIssueType;
import com.sugamflow.school.support.ticket.model.SupportProduct;
import com.sugamflow.school.support.ticket.model.SupportTicket;
import com.sugamflow.school.support.ticket.model.SupportTicketPriority;
import com.sugamflow.school.support.ticket.model.SupportTicketStatus;

public record SupportTicketResponse(
    Long id,
    String ticketNumber,
    SupportProduct product,
    String organizationId,
    String shopId,
    SupportIssueType issueType,
    String subject,
    String description,
    SupportTicketPriority priority,
    SupportTicketStatus status,
    String contactEmail,
    String contactMobile,
    String moduleName,
    String appVersion,
    String deviceInfo,
    String submittedBy,
    String assignedTo,
    LocalDateTime slaDueAt,
    LocalDateTime firstResponseAt,
    LocalDateTime resolvedAt,
    LocalDateTime closedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<SupportAttachmentResponse> attachments,
    List<SupportCommentResponse> comments) {

  public static SupportTicketResponse from(
      SupportTicket ticket,
      List<SupportAttachmentResponse> attachments,
      List<SupportCommentResponse> comments) {
    return new SupportTicketResponse(
        ticket.getId(),
        ticket.getTicketNumber(),
        ticket.getProduct(),
        ticket.getOrganizationId(),
        ticket.getShopId(),
        ticket.getIssueType(),
        ticket.getSubject(),
        ticket.getDescription(),
        ticket.getPriority(),
        ticket.getStatus(),
        ticket.getContactEmail(),
        ticket.getContactMobile(),
        ticket.getModuleName(),
        ticket.getAppVersion(),
        ticket.getDeviceInfo(),
        ticket.getSubmittedBy(),
        ticket.getAssignedTo(),
        ticket.getSlaDueAt(),
        ticket.getFirstResponseAt(),
        ticket.getResolvedAt(),
        ticket.getClosedAt(),
        ticket.getCreatedAt(),
        ticket.getUpdatedAt(),
        attachments,
        comments);
  }

  public static SupportTicketResponse summary(SupportTicket ticket) {
    return from(ticket, List.of(), List.of());
  }
}
