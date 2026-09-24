package com.sugamflow.school.support.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.support.ticket.model.SupportTicketComment;

public interface SupportTicketCommentRepository
    extends JpaRepository<SupportTicketComment, Long> {
  List<SupportTicketComment> findByTicketIdAndInternalNoteFalseOrderByCreatedAtAsc(Long ticketId);

  List<SupportTicketComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
