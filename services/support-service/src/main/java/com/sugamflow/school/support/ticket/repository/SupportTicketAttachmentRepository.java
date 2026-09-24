package com.sugamflow.school.support.ticket.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.support.ticket.model.SupportTicketAttachment;

public interface SupportTicketAttachmentRepository
    extends JpaRepository<SupportTicketAttachment, Long> {
  List<SupportTicketAttachment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

  long countByTicketId(Long ticketId);

  Optional<SupportTicketAttachment> findByIdAndTicketId(Long id, Long ticketId);
}
