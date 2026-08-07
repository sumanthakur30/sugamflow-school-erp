package com.sugamflow.school.support.ticket.dto;

import java.time.LocalDateTime;

import com.sugamflow.school.support.ticket.model.SupportTicketComment;

public record SupportCommentResponse(
    Long id, String body, String author, boolean staffResponse, LocalDateTime createdAt) {

  public static SupportCommentResponse from(SupportTicketComment comment) {
    return new SupportCommentResponse(
        comment.getId(),
        comment.getBody(),
        comment.getAuthor(),
        comment.isStaffResponse(),
        comment.getCreatedAt());
  }
}
