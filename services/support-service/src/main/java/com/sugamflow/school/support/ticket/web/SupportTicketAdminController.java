package com.sugamflow.school.support.ticket.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sugamflow.school.support.ticket.dto.AdminSupportCommentRequest;
import com.sugamflow.school.support.ticket.dto.AssignSupportTicketRequest;
import com.sugamflow.school.support.ticket.dto.SupportTicketResponse;
import com.sugamflow.school.support.ticket.dto.UpdateSupportTicketPriorityRequest;
import com.sugamflow.school.support.ticket.dto.UpdateSupportTicketStatusRequest;
import com.sugamflow.school.support.ticket.model.SupportProduct;
import com.sugamflow.school.support.ticket.model.SupportTicketStatus;
import com.sugamflow.school.support.ticket.service.SupportTicketService;

import jakarta.validation.Valid;

/**
 * Platform Support queue APIs used by SugamFlow Superadmin ({@code /admin/support-tickets}).
 * Path matches shop-management-ui {@code gatewayResourceUrl('admin/support/tickets')}.
 */
@RestController
@RequestMapping("/api/v1/admin/support/tickets")
public class SupportTicketAdminController {
  private final SupportTicketService supportTicketService;

  public SupportTicketAdminController(SupportTicketService supportTicketService) {
    this.supportTicketService = supportTicketService;
  }

  @GetMapping
  public Page<SupportTicketResponse> list(
      @RequestParam(required = false) SupportTicketStatus status,
      @RequestParam(required = false) SupportProduct product,
      Pageable pageable) {
    return supportTicketService.listAllTickets(status, product, pageable);
  }

  @GetMapping("/{ticketId}")
  public SupportTicketResponse get(@PathVariable Long ticketId) {
    return supportTicketService.getAdminTicket(ticketId);
  }

  @PutMapping("/{ticketId}/status")
  public SupportTicketResponse updateStatus(
      @PathVariable Long ticketId, @Valid @RequestBody UpdateSupportTicketStatusRequest request) {
    return supportTicketService.updateStatus(ticketId, request.status());
  }

  @PutMapping("/{ticketId}/priority")
  public SupportTicketResponse updatePriority(
      @PathVariable Long ticketId, @Valid @RequestBody UpdateSupportTicketPriorityRequest request) {
    return supportTicketService.updatePriority(ticketId, request.priority());
  }

  @PutMapping("/{ticketId}/assign")
  public SupportTicketResponse assign(
      @PathVariable Long ticketId, @Valid @RequestBody AssignSupportTicketRequest request) {
    return supportTicketService.assignTicket(ticketId, request.assignedTo());
  }

  @PostMapping("/{ticketId}/comments")
  public SupportTicketResponse addComment(
      @PathVariable Long ticketId, @Valid @RequestBody AdminSupportCommentRequest request) {
    return supportTicketService.addAdminComment(ticketId, request);
  }
}
