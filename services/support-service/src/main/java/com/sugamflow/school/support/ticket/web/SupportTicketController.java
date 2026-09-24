package com.sugamflow.school.support.ticket.web;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.support.ticket.dto.AddSupportCommentRequest;
import com.sugamflow.school.support.ticket.dto.CreateSupportTicketRequest;
import com.sugamflow.school.support.ticket.dto.SupportAttachmentResponse;
import com.sugamflow.school.support.ticket.dto.SupportTicketResponse;
import com.sugamflow.school.support.ticket.dto.UpdateSupportTicketRequest;
import com.sugamflow.school.support.ticket.model.SupportTicketAttachment;
import com.sugamflow.school.support.ticket.service.SupportAttachmentStorageService;
import com.sugamflow.school.support.ticket.service.SupportTicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/support/tickets")
public class SupportTicketController {
  private final SupportTicketService supportTicketService;
  private final SupportAttachmentStorageService storageService;

  public SupportTicketController(
      SupportTicketService supportTicketService, SupportAttachmentStorageService storageService) {
    this.supportTicketService = supportTicketService;
    this.storageService = storageService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SupportTicketResponse> create(
      @Valid @RequestPart("ticket") CreateSupportTicketRequest ticket,
      @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments)
      throws IOException {
    return ApiResponse.ok(supportTicketService.createTicket(ticket, attachments));
  }

  @GetMapping
  public ApiResponse<Page<SupportTicketResponse>> list(Pageable pageable) {
    return ApiResponse.ok(supportTicketService.listMyTickets(pageable));
  }

  @GetMapping("/{ticketId}")
  public ApiResponse<SupportTicketResponse> get(@PathVariable Long ticketId) {
    return ApiResponse.ok(supportTicketService.getMyTicket(ticketId));
  }

  @PostMapping("/{ticketId}/comments")
  public ApiResponse<SupportTicketResponse> addComment(
      @PathVariable Long ticketId, @Valid @RequestBody AddSupportCommentRequest request) {
    return ApiResponse.ok(supportTicketService.addCustomerComment(ticketId, request));
  }

  @PostMapping("/{ticketId}/close")
  public ApiResponse<SupportTicketResponse> closePost(@PathVariable Long ticketId) {
    return ApiResponse.ok(supportTicketService.closeMyTicket(ticketId));
  }

  @PutMapping("/{ticketId}/close")
  public ApiResponse<SupportTicketResponse> closePut(@PathVariable Long ticketId) {
    return ApiResponse.ok(supportTicketService.closeMyTicket(ticketId));
  }

  @PutMapping("/{ticketId}")
  public ApiResponse<SupportTicketResponse> update(
      @PathVariable Long ticketId, @Valid @RequestBody UpdateSupportTicketRequest request) {
    return ApiResponse.ok(supportTicketService.updateMyTicket(ticketId, request));
  }

  @PostMapping(value = "/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SupportAttachmentResponse> addAttachment(
      @PathVariable Long ticketId, @RequestPart("file") MultipartFile file) throws IOException {
    return ApiResponse.ok(supportTicketService.addAttachment(ticketId, file));
  }

  @GetMapping("/{ticketId}/attachments/{attachmentId}")
  public ResponseEntity<Resource> download(
      @PathVariable Long ticketId, @PathVariable Long attachmentId) throws IOException {
    SupportTicketAttachment attachment =
        supportTicketService.requireAttachment(ticketId, attachmentId);
    byte[] bytes = Files.readAllBytes(storageService.resolvePath(attachment.getStoragePath()));
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"" + attachment.getFileName() + "\"")
        .contentType(MediaType.parseMediaType(attachment.getContentType()))
        .body(new ByteArrayResource(bytes));
  }
}
