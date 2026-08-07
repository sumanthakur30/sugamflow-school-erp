package com.sugamflow.school.support.ticket.dto;

import java.time.LocalDateTime;

import com.sugamflow.school.support.ticket.model.SupportTicketAttachment;

public record SupportAttachmentResponse(
    Long id, String fileName, String contentType, long fileSize, LocalDateTime createdAt) {

  public static SupportAttachmentResponse from(SupportTicketAttachment attachment) {
    return new SupportAttachmentResponse(
        attachment.getId(),
        attachment.getFileName(),
        attachment.getContentType(),
        attachment.getFileSize(),
        attachment.getCreatedAt());
  }
}
